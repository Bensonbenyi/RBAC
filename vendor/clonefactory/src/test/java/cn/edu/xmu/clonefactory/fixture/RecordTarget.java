package cn.edu.xmu.clonefactory.fixture;

import cn.edu.xmu.clonefactory.CopyFrom;

@CopyFrom({SourceBean.class})
public record RecordTarget(String name, Integer age, String extra) {
}