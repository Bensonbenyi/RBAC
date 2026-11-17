package cn.edu.xmu.clonefactory.fixture;

import cn.edu.xmu.clonefactory.CopyNotNullFrom;

@CopyNotNullFrom({PartialSource.class})
public record NotNullRecordTarget(String name, Integer age) {
}