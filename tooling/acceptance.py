#!/usr/bin/env python3
"""验证真实 Swarm 两系统及 RBAC1，测试关联和学期在 finally 中清理。"""
import argparse
from datetime import date
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--base', required=True)
parser.add_argument('--version', required=True, help='被验收部署的版本')
parser.add_argument('--strategy', choices=['structured', 'ddd'], required=True)
parser.add_argument('--report', required=True)
parser.add_argument('--origin', help='验证已部署的课程前端来源，默认使用部署入口')
args = parser.parse_args()
checks = []
phone = os.environ['RBAC_DEMO_PHONE']
direct_http = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(path, method='GET', body=None, token=None, expected=200, cross_origin=False):
    headers = {'Content-Type': 'application/json'} if body is not None else {}
    if cross_origin:
        # 使用测试 Host 让同一公网入口能够按浏览器跨域规则响应预检。
        origin = args.origin or args.base
        headers.update({'Origin': origin, 'Host': 'rbac-cors-probe.test'})
        if method == 'OPTIONS':
            headers.update({'Access-Control-Request-Method': 'GET',
                            'Access-Control-Request-Headers': 'authorization,x-target-system'})
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(
        args.base + path,
        data=json.dumps(body).encode() if body is not None else None,
        method=method, headers=headers)
    try:
        open_request = direct_http.open if cross_origin else urllib.request.urlopen
        with open_request(request, timeout=20) as response:
            status, raw, returned = response.status, response.read(), response.headers
    except urllib.error.HTTPError as error:
        status, raw, returned = error.code, error.read(), error.headers
    assert status == expected, (path, status, raw.decode())
    if cross_origin:
        assert returned.get('Access-Control-Allow-Origin') == origin, (path, returned)
        assert returned.get('Access-Control-Allow-Credentials') is None
        if method == 'OPTIONS':
            assert 'GET' in returned.get('Access-Control-Allow-Methods', '')
            assert 'authorization' in returned.get('Access-Control-Allow-Headers', '').lower()
    checks.append({'method': method, 'path': path, 'status': status})
    return json.loads(raw) if raw else None


def login(user, system):
    return call('/auth/login', 'POST', {'username': user, 'password': phone,
                                      'systems': [system]})['accessToken']


def pages(path, token):
    result = []
    for page in range(100):
        data = call(f'{path}?page={page}&size=100', token=token)
        result.extend(data['items'])
        if len(result) >= data['total']:
            return result
    raise AssertionError('Unexpected catalog size')


admin = course_admin = reviewer = viewer = scale_teacher = scale_student = None
roles, links, grants, terms = [], [], [], []
assigned = False
stamp = str(int(time.time()))
try:
    admin = login('admin', 'rbac')
    reviewer = call('/course-api/login', 'POST', {
        'role': 'teacher', 'account': 'reviewer', 'password': phone})['accessToken']
    viewer = call('/course-api/login', 'POST', {
        'role': 'teacher', 'account': 'viewer', 'password': phone})['accessToken']
    call('/course-api/teacher/courses', token=reviewer)
    course_admin = login('admin', 'course-project-management')
    query = urllib.parse.urlencode({'keyword': '规模演示组织'})
    directory = call(f'/auth/departments?{query}', token=course_admin)
    assert directory['total'] == 1 and directory['items'][0]['hasChildren']
    call('/auth/departments', token=viewer, expected=403)
    assert call('/course-api/auth/permissions/MCO', token=viewer)['allowed']
    assert not call('/course-api/auth/permissions/MTR', token=viewer)['allowed']
    me = call('/course-api/me', token=viewer)
    assert 'MCO' in me['permissions'] and 'CV' not in me['permissions']
    users = pages('/api/users', admin)
    viewer_id = next(u['id'] for u in users if u['username'] == 'viewer')
    assert all('phone' not in user for user in users)
    identity = call(f'/auth/users/{viewer_id}', token=course_admin)
    assert identity['no'] == 'viewer'
    department_id = call('/auth/me', token=course_admin)['departmentId']
    members = call(f'/auth/departments/{department_id}/users?keyword=viewer', token=course_admin)
    assert any(user['userId'] == viewer_id for user in members['items'])
    for path in ['/auth/permissions/MTE', '/auth/departments',
                 f'/auth/departments/{department_id}/users']:
        call(path, 'OPTIONS', cross_origin=True)
        call(path, token=reviewer, cross_origin=True)
        call(path, expected=401, cross_origin=True)
    assert call('/auth/permissions/MTE', token=reviewer, cross_origin=True)['allowed']
    assert call('/auth/permissions/MTEL', token=reviewer)['allowed']
    assert not call('/auth/permissions/MTE', token=viewer, cross_origin=True)['allowed']
    call('/auth/departments', token=viewer, expected=403, cross_origin=True)
    for role, number in [('teacher', 1), ('student', 191)]:
        account = f'ooad-load-2026-{number:05}'
        login_body = {'role': role, 'account': account, 'password': str(19900000000 + number)}
        result = call('/course-api/login', 'POST', login_body)
        credential = result['accessToken']
        if role == 'teacher':
            scale_teacher = credential
        else:
            scale_student = credential
        profile = call('/course-api/me', token=credential)
        assert profile['roles'] == [role] and profile['number'] == account
        assert profile[f'{role}Id'] is not None
        path = '/course-api/teacher/courses' if role == 'teacher' else '/course-api/me/courses'
        terms_for_user = call('/course-api/terms', token=credential)['items']
        demo_term = next(t for t in terms_for_user if t['name'] == '2026 秋季课程演示')
        own_courses = call(f"{path}?termId={demo_term['id']}", token=credential)
        demo_course = next(c for c in own_courses['items'] if c['code'] == 'RBAC-IDENTITY-DEMO')
        call(f"/course-api/courses/{demo_course['id']}/milestones", token=credential)
        if role == 'student':
            call(f"/course-api/me/courses/{demo_course['id']}/grades", token=credential)
        call('/course-api/teachers', token=credential, expected=403)
        other_role = 'student' if role == 'teacher' else 'teacher'
        call('/course-api/login', 'POST', {**login_body, 'role': other_role}, expected=404)
    teachers = call('/course-api/teachers?keyword=ooad-load-2026-00001', token=reviewer)
    assert teachers['total'] == 1
    systems = pages('/api/systems', admin)
    course_id = next(s['id'] for s in systems if s['systemKey'] == 'course-project-management')
    permissions = pages('/api/systems/' + course_id + '/permissions', admin)
    codes = {p['code']: p['id'] for p in permissions}
    courses = call('/course-api/admin/courses?page=1&pageSize=20', token=reviewer)
    assert courses['items'], 'The course demo needs one existing synthetic course'
    course = courses['items'][0]
    unrelated = next(item for item in courses['items'] if item['code'] != 'RBAC-IDENTITY-DEMO')
    call(f"/course-api/courses/{unrelated['id']}/milestones", token=scale_student, expected=403)
    call('/course-api/courses/' + str(course['id']), token=reviewer)
    call('/course-api/courses/' + str(course['id']) + '/review', 'POST',
         {'decision': 'approved', 'comment': None}, viewer, 403)
    term_input = {'name': '验收学期 ' + stamp, 'startDate': '2026-09-01', 'endDate': '2027-01-31'}
    call('/course-api/terms', 'POST', term_input, viewer, 403)
    for name in ['高级', '中间', '基础']:
        roles.append(call('/api/roles', 'POST', {'name': '验收 ' + stamp + ' ' + name}, admin, 201)['id'])
    senior, middle, base = roles
    for code in ['TC', 'MTR', 'MTRC']:
        path = f'/api/roles/{base}/permissions'
        call(path, 'POST', {'permissionId': codes[code]}, admin, 201)
        grants.append(f'{path}/{codes[code]}')
    call(path, 'POST', {'permissionId': codes[code]}, admin, 409)
    for a, b in [(senior, middle), (middle, base), (senior, base)]:
        path = f'/api/roles/{a}/inherited-roles'
        call(path, 'POST', {'juniorId': b}, admin, 201)
        links.append(f'{path}/{b}')
    call(f'/api/roles/{senior}/inherited-roles', 'POST', {'juniorId': base}, admin, 409)
    cycle = call(f'/api/roles/{base}/inherited-roles', 'POST', {'juniorId': senior}, admin, 409)
    assert cycle['code'] == 'ROLE_CYCLE'
    call(f'/api/roles/{senior}/inherited-roles', 'POST', {'juniorId': senior}, admin, 409)
    call(f'/api/roles/{senior}', 'DELETE', token=admin, expected=409)
    effective = call(f'/api/roles/{senior}/effective-permissions', token=admin)
    assert len(effective) == 3 and {p['code'] for p in effective} == {'TC', 'MTR', 'MTRC'}
    call(f'/api/users/{viewer_id}/roles', 'POST', {'roleId': senior}, admin, 201)
    assigned = True
    refreshed = call('/course-api/me', token=viewer)
    assert {'MCO', 'MTR', 'MTRC'} <= set(refreshed['permissions'])
    assert call('/auth/api-permissions/createTerm', token=viewer) == {
        'operationId': 'createTerm', 'allowed': True}
    assert not call('/auth/permissions/TC', token=viewer)['allowed']
    term = call('/course-api/terms', 'POST', term_input, viewer, 201)
    terms.append(str(term['id']))
    call('/course-api/terms/' + str(term['id']), 'PATCH',
         {**term_input, 'name': term_input['name'] + ' 修订'}, reviewer)
    # Removing the shortcut preserves the transitive path.
    call(links[-1], 'DELETE', token=admin, expected=204)
    assert call('/auth/api-permissions/createTerm', token=viewer)['allowed']
    # Removing the remaining path immediately denies the same token.
    call(links[1], 'DELETE', token=admin, expected=204)
    assert not call('/auth/api-permissions/createTerm', token=viewer)['allowed']
    call('/course-api/terms', 'POST', term_input, viewer, 403)
    assert set(call('/course-api/me', token=viewer)['permissions']) == set(me['permissions'])
    for path in ['/api/roles', '/api/departments', '/api/systems', '/api/systems/' + course_id + '/permissions', '/auth/me']:
        call(path, token=admin)
    call('/auth/logout', 'POST', token=viewer, expected=204)
    call('/course-api/terms', token=viewer, expected=401)
    viewer = None
finally:
    # Each cleanup operation remains visible in the report.
    for term_id in terms:
        call('/course-api/terms/' + term_id, 'DELETE', token=reviewer, expected=204)
    if assigned:
        call(f'/api/users/{viewer_id}/roles/{roles[0]}', 'DELETE', token=admin, expected=204)
    for path in reversed(links):
        call(path, 'DELETE', token=admin, expected=204)
    for path in grants:
        call(path, 'DELETE', token=admin, expected=204)
    for role_id in roles:
        call('/api/roles/' + role_id, 'DELETE', token=admin, expected=204)
    for token in [scale_student, scale_teacher, course_admin, viewer, reviewer, admin]:
        if token:
            call('/auth/logout', 'POST', token=token, expected=204)
report = {'version': args.version, 'date': date.today().isoformat(), 'strategy': args.strategy,
          'base': args.base, 'result': 'passed', 'checks': checks,
          'fixturesCleaned': True}
Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print(f'{args.strategy}: {len(checks)} HTTP checks passed; synthetic fixtures cleaned')
