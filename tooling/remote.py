#!/usr/bin/env python3
"""Execute SSH operations; read the password once without storing or logging it."""
import argparse,getpass,json,sys
import pexpect
p=argparse.ArgumentParser();p.add_argument('host',nargs='?');p.add_argument('--command');p.add_argument('--copy',nargs=2);p.add_argument('--batch');a=p.parse_args()
password=getpass.getpass('SSH password: ')
opts=['-o','StrictHostKeyChecking=accept-new','-o','ConnectTimeout=10','-o','ServerAliveInterval=15']
operations=json.load(open(a.batch)) if a.batch else [{'host':a.host,'command':a.command,'copy':a.copy}]
for operation in operations:
    host=operation['host'];copy=operation.get('copy')
    args=['scp','-q',*opts,copy[0],'root@'+host+':'+copy[1]] if copy else ['ssh',*opts,'root@'+host,operation['command']]
    print('Copying files to' if copy else 'Running command on',host,flush=True)
    child=pexpect.spawn(args[0],args[1:],encoding='utf-8',timeout=3600,echo=False)
    child.expect(r'(?i)password:|$',timeout=2)
    # Use a read-only logfile: SSH credential input never enters the log.
    child.logfile_read=sys.stdout
    if 'password:' in (child.after or '').lower():child.sendline(password)
    while True:
        i=child.expect([r'(?i)password:',pexpect.EOF,pexpect.TIMEOUT],timeout=30)
        if i==0:child.sendline(password)
        elif i==1:break
    child.close()
    code=child.exitstatus or child.signalstatus or 0
    if code:sys.exit(code)
