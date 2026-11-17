package cn.edu.xmu.clonefactory.fixture;

import cn.edu.xmu.clonefactory.CopyTo;

@CopyTo({MutableTarget2.class})
public record RecordSource(String name, Integer age) {
}