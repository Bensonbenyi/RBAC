package cn.edu.xmu.clonefactory.fixture;

import cn.edu.xmu.clonefactory.CopyFrom;

@CopyFrom({SourceBean.class})
public class MutableTarget {
    private String name;
    private Integer age;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }
}