package cn.edu.xmu.clonefactory.fixture;

import cn.edu.xmu.clonefactory.CopyFrom;

@CopyFrom({SourceBean.class})
public final class FinalTarget {
    private final String name;
    private final Integer age;
    private final String extra;

    public FinalTarget(String name, Integer age, String extra) {
        this.name = name;
        this.age = age;
        this.extra = extra;
    }

    public String getName() {
        return name;
    }

    public Integer getAge() {
        return age;
    }

    public String getExtra() {
        return extra;
    }
}