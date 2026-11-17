package cn.edu.xmu.clonefactory.fixture;

public class SourceBean {
    private String name;
    private Integer age;

    public SourceBean() {
    }

    public SourceBean(String name, Integer age) {
        this.name = name;
        this.age = age;
    }

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