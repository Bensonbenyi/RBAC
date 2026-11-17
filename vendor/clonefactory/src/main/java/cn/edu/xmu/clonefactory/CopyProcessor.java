package cn.edu.xmu.clonefactory;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * CloneFactory 注解处理器。
 *
 * <p>支持四种拷贝注解：{@link CopyFrom}、{@link CopyTo}、
 * {@link CopyNotNullFrom} 和 {@link CopyNotNullTo}。</p>
 *
 * <p>生成策略：</p>
 * <ul>
 *     <li>普通可变类：通过 setter 或 public 字段赋值；</li>
 *     <li>record：通过规范构造器创建新对象；</li>
 *     <li>包含 final 字段的类：通过全参数构造器创建新对象；</li>
 *     <li>copyNotNull：引用类型为 null 时保留 target 原值。</li>
 * </ul>
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes({
        "cn.edu.xmu.clonefactory.CopyFrom",
        "cn.edu.xmu.clonefactory.CopyNotNullFrom",
        "cn.edu.xmu.clonefactory.CopyTo",
        "cn.edu.xmu.clonefactory.CopyNotNullTo"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class CopyProcessor extends AbstractProcessor {

    private static final String PACKAGE_NAME = "cn.edu.xmu.clonefactory.util";
    private static final String GENERATED_CLASS_NAME = "CloneFactory";

    private Messager messager;
    private boolean fileGenerated;

    private static final List<AnnotationConfig> CONFIGS = List.of(
            new AnnotationConfig(CopyFrom.class, CopyFrom.Exclude.class, CopyFrom.Of.class,
                    "copy", false, true),
            new AnnotationConfig(CopyTo.class, CopyTo.Exclude.class, CopyTo.Of.class,
                    "copy", false, false),
            new AnnotationConfig(CopyNotNullFrom.class, CopyNotNullFrom.Exclude.class, CopyNotNullFrom.Of.class,
                    "copyNotNull", true, true),
            new AnnotationConfig(CopyNotNullTo.class, CopyNotNullTo.Exclude.class, CopyNotNullTo.Of.class,
                    "copyNotNull", true, false)
    );

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty() || fileGenerated) {
            return false;
        }

        TypeSpec.Builder factory = TypeSpec.classBuilder(GENERATED_CLASS_NAME)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

        Set<String> generatedSignatures = new LinkedHashSet<>();
        boolean generatedAny = false;

        for (AnnotationConfig config : CONFIGS) {
            for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(config.annotationType())) {
                if (!isClassOrRecord(annotatedElement)) {
                    continue;
                }

                TypeElement annotatedType = (TypeElement) annotatedElement;
                for (DeclaredType relatedType : getClassValues(annotatedType, "value", config.annotationType())) {
                    TypeElement relatedElement = (TypeElement) relatedType.asElement();
                    TypeElement target = config.copyFrom() ? annotatedType : relatedElement;
                    TypeElement source = config.copyFrom() ? relatedElement : annotatedType;

                    MethodSpec method = generateCopyMethod(config, target, source);
                    if (method == null) {
                        continue;
                    }

                    String signature = config.methodName() + "(" + target.getQualifiedName() + "," + source.getQualifiedName() + ")";
                    if (generatedSignatures.add(signature)) {
                        factory.addMethod(method);
                        generatedAny = true;
                    }
                }
            }
        }

        if (!generatedAny) {
            return true;
        }

        try {
            JavaFile.builder(PACKAGE_NAME, factory.build()).build().writeTo(processingEnv.getFiler());
            fileGenerated = true;
        } catch (Exception exception) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "生成 CloneFactory 失败: " + exception.getMessage());
        }
        return true;
    }

    /**
     * 为一组 target/source 生成 copy 或 copyNotNull 方法。
     */
    private MethodSpec generateCopyMethod(AnnotationConfig config, TypeElement target, TypeElement source) {
        Map<String, PropertyInfo> targetProperties = properties(target);
        Map<String, PropertyInfo> sourceProperties = properties(source);

        MethodSpec.Builder method = MethodSpec.methodBuilder(config.methodName())
                .addJavadoc("Copy matching properties from $T to $T.\n", source, target)
                .addJavadoc("Generated from @$L on $T.\n", config.annotationType().getSimpleName(), target)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(TypeName.get(target.asType()))
                .addParameter(TypeName.get(target.asType()), "target")
                .addParameter(TypeName.get(source.asType()), "source");

        if (requiresConstructor(target)) {
            List<String> constructorProperties = constructorPropertyNames(target);
            if (constructorProperties == null) {
                return null;
            }

            List<CodeBlock> arguments = new ArrayList<>();
            for (String propertyName : constructorProperties) {
                CodeBlock argument = constructorArgument(config, target, source,
                        targetProperties, sourceProperties, propertyName);
                if (argument == null) {
                    return null;
                }
                arguments.add(argument);
            }
            method.addStatement("return new $T($L)", TypeName.get(target.asType()),
                    CodeBlock.join(arguments, ", "));
            return method.build();
        }

        boolean generatedStatement = false;
        for (PropertyInfo targetProperty : targetProperties.values()) {
            if (targetProperty.writer() == null) {
                continue;
            }

            PropertyInfo sourceProperty = sourceProperties.get(targetProperty.name());
            if (sourceProperty == null || sourceProperty.reader() == null) {
                continue;
            }
            if (isExcluded(config, target, source, targetProperty, sourceProperty)) {
                continue;
            }

            CodeBlock sourceRead = read(sourceProperty, "source");
            if (config.copyNotNull() && !isPrimitive(sourceProperty.type())) {
                method.beginControlFlow("if ($L != null)", sourceRead);
                write(method, targetProperty, sourceRead);
                method.endControlFlow();
            } else {
                write(method, targetProperty, sourceRead);
            }
            generatedStatement = true;
        }

        if (!generatedStatement) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "没有为 " + target.getQualifiedName() + " <- " + source.getQualifiedName()
                            + " 生成可复制属性。请检查 getter/setter、final 字段或构造器。");
        }

        method.addStatement("return target");
        return method.build();
    }

    /**
     * 生成不可变目标对象的构造器参数。
     */
    private CodeBlock constructorArgument(AnnotationConfig config,
                                          TypeElement target,
                                          TypeElement source,
                                          Map<String, PropertyInfo> targetProperties,
                                          Map<String, PropertyInfo> sourceProperties,
                                          String propertyName) {
        PropertyInfo sourceProperty = sourceProperties.get(propertyName);
        PropertyInfo targetProperty = targetProperties.get(propertyName);

        boolean copySource = sourceProperty != null
                && sourceProperty.reader() != null
                && !isExcluded(config, target, source, targetProperty, sourceProperty);

        if (copySource) {
            CodeBlock sourceRead = read(sourceProperty, "source");
            if (config.copyNotNull() && !isPrimitive(sourceProperty.type())) {
                if (targetProperty == null || targetProperty.reader() == null) {
                    return sourceRead;
                }
                CodeBlock targetRead = read(targetProperty, "target");
                return CodeBlock.of("$L != null ? $L : $L", sourceRead, sourceRead, targetRead);
            }
            return sourceRead;
        }

        if (targetProperty != null && targetProperty.reader() != null) {
            return read(targetProperty, "target");
        }

        messager.printMessage(Diagnostic.Kind.ERROR,
                "无法为不可变类型 " + target.getQualifiedName() + " 的属性 " + propertyName
                        + " 找到 source 或 target 的可读属性。");
        return null;
    }

    private void write(MethodSpec.Builder method, PropertyInfo targetProperty, CodeBlock value) {
        if (targetProperty.writer().equals(targetProperty.name())) {
            method.addStatement("target.$L = $L", targetProperty.name(), value);
        } else {
            method.addStatement("target.$L($L)", targetProperty.writer(), value);
        }
    }

    private CodeBlock read(PropertyInfo property, String variableName) {
        if (property.reader().equals(property.name()) && property.field() != null
                && property.field().getModifiers().contains(Modifier.PUBLIC)) {
            return CodeBlock.of("$L.$L", variableName, property.name());
        }
        return CodeBlock.of("$L.$L()", variableName, property.reader());
    }

    /**
     * record 或包含 final 实例字段的目标类型需要通过构造器创建新对象。
     */
    private boolean requiresConstructor(TypeElement target) {
        if (target.getKind() == ElementKind.RECORD) {
            return true;
        }
        return instanceFields(target).stream()
                .anyMatch(field -> field.getModifiers().contains(Modifier.FINAL));
    }

    /**
     * 返回目标构造器参数对应的属性名，顺序必须与构造器一致。
     */
    private List<String> constructorPropertyNames(TypeElement target) {
        if (target.getKind() == ElementKind.RECORD) {
            return target.getRecordComponents().stream()
                    .map(component -> component.getSimpleName().toString())
                    .toList();
        }

        List<VariableElement> fields = declaredInstanceFields(target);
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(target.getEnclosedElements()).stream()
                .filter(constructor -> constructor.getModifiers().contains(Modifier.PUBLIC))
                .toList();

        ExecutableElement constructor = constructors.stream()
                .max((left, right) -> Integer.compare(left.getParameters().size(), right.getParameters().size()))
                .orElse(null);

        // Lombok 的 @AllArgsConstructor/@Value 在部分处理阶段可能尚未暴露构造器。
        if ((constructor == null || constructor.getParameters().isEmpty()) && hasLombokAllArgsConstructor(target)) {
            return fields.stream().map(field -> field.getSimpleName().toString()).toList();
        }

        if (constructor == null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "类型 " + target.getQualifiedName() + " 含有 final 字段，但找不到 public 构造器。");
            return null;
        }

        List<String> propertyNames = new ArrayList<>();
        List<? extends VariableElement> parameters = constructor.getParameters();
        for (int index = 0; index < parameters.size(); index++) {
            VariableElement parameter = parameters.get(index);
            String parameterName = parameter.getSimpleName().toString();
            String matched = fields.stream()
                    .map(field -> field.getSimpleName().toString())
                    .filter(name -> name.equals(parameterName))
                    .findFirst()
                    .orElse(null);

            if (matched == null && parameters.size() == fields.size()) {
                matched = fields.get(index).getSimpleName().toString();
            }

            if (matched == null) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "无法把构造器参数 " + parameterName + " 映射到 " + target.getQualifiedName() + " 的字段。");
                return null;
            }
            propertyNames.add(matched);
        }
        return propertyNames;
    }

    /**
     * 构建目标类型的属性表。record 使用组件访问器，普通类使用 getter/setter、
     * Lombok 注解或 public 字段。
     */
    private Map<String, PropertyInfo> properties(TypeElement type) {
        Map<String, PropertyInfo> properties = new LinkedHashMap<>();

        if (type.getKind() == ElementKind.RECORD) {
            for (RecordComponentElement component : type.getRecordComponents()) {
                String name = component.getSimpleName().toString();
                properties.put(name, new PropertyInfo(
                        name,
                        component.asType(),
                        findField(type, name),
                        component.getAccessor().getSimpleName().toString(),
                        null,
                        true,
                        false
                ));
            }
            return properties;
        }

        List<ExecutableElement> methods = allMethods(type);
        for (VariableElement field : instanceFields(type)) {
            String name = field.getSimpleName().toString();
            boolean isFinal = field.getModifiers().contains(Modifier.FINAL);
            boolean isPublic = field.getModifiers().contains(Modifier.PUBLIC);
            String reader = findReader(type, methods, field);
            String writer = isFinal ? null : findWriter(type, methods, field);
            properties.put(name, new PropertyInfo(name, field.asType(), field, reader, writer, isFinal, isPublic));
        }

        // 兼容只有 getter/setter、没有对应字段的虚拟属性。
        for (ExecutableElement method : methods) {
            String methodName = method.getSimpleName().toString();
            if (method.getParameters().isEmpty() && method.getReturnType().getKind() != TypeKind.VOID) {
                String propertyName = propertyNameFromReader(methodName);
                if (propertyName != null && !properties.containsKey(propertyName)) {
                    properties.put(propertyName, new PropertyInfo(
                            propertyName, method.getReturnType(), null, methodName, null, false, false));
                }
            }
            if (method.getParameters().size() == 1 && methodName.startsWith("set") && methodName.length() > 3) {
                String propertyName = decapitalize(methodName.substring(3));
                PropertyInfo existing = properties.get(propertyName);
                if (existing == null) {
                    properties.put(propertyName, new PropertyInfo(
                            propertyName, method.getParameters().get(0).asType(), null, null, methodName, false, false));
                } else if (existing.writer() == null) {
                    properties.put(propertyName, new PropertyInfo(
                            existing.name(), existing.type(), existing.field(), existing.reader(), methodName,
                            existing.finalField(), existing.publicField()));
                }
            }
        }
        return properties;
    }

    private String findReader(TypeElement owner, List<ExecutableElement> methods, VariableElement field) {
        String name = field.getSimpleName().toString();
        String getter = "get" + capitalize(name);
        String booleanGetter = "is" + capitalize(name);
        if (methodExists(methods, getter) || methodExists(methods, booleanGetter)
                || hasAnnotation(field, "lombok.Getter") || hasAnnotation(owner, "lombok.Getter")
                || hasAnnotation(owner, "lombok.Data") || hasAnnotation(owner, "lombok.Value")) {
            return getter;
        }
        if (field.getModifiers().contains(Modifier.PUBLIC)) {
            return name;
        }
        return null;
    }

    private String findWriter(TypeElement owner, List<ExecutableElement> methods, VariableElement field) {
        String name = field.getSimpleName().toString();
        String setter = "set" + capitalize(name);
        if (methodExists(methods, setter) || hasAnnotation(field, "lombok.Setter")
                || hasAnnotation(owner, "lombok.Setter") || hasAnnotation(owner, "lombok.Data")) {
            return setter;
        }
        if (field.getModifiers().contains(Modifier.PUBLIC) && !field.getModifiers().contains(Modifier.FINAL)) {
            return name;
        }
        return null;
    }

    private boolean isExcluded(AnnotationConfig config,
                               TypeElement target,
                               TypeElement source,
                               PropertyInfo targetProperty,
                               PropertyInfo sourceProperty) {
        String oppositeType = config.copyFrom()
                ? source.getQualifiedName().toString()
                : target.getQualifiedName().toString();

        Set<String> excludes = new LinkedHashSet<>();
        Set<String> includes = new LinkedHashSet<>();
        collectRuleValues(targetProperty, config.excludeType(), config.ofType(), excludes, includes);
        collectRuleValues(sourceProperty, config.excludeType(), config.ofType(), excludes, includes);

        if (!includes.isEmpty() && !includes.contains(oppositeType)) {
            return true;
        }
        return excludes.contains(oppositeType);
    }

    private void collectRuleValues(PropertyInfo property,
                                   Class<? extends Annotation> excludeType,
                                   Class<? extends Annotation> ofType,
                                   Set<String> excludes,
                                   Set<String> includes) {
        if (property == null) {
            return;
        }
        List<Element> elements = new ArrayList<>();
        if (property.field() != null) {
            elements.add(property.field());
        }
        TypeElement owner = property.field() == null ? null : (TypeElement) property.field().getEnclosingElement();
        if (owner != null) {
            for (ExecutableElement method : allMethods(owner)) {
                if (method.getSimpleName().contentEquals(property.name())
                        || method.getSimpleName().contentEquals("get" + capitalize(property.name()))
                        || method.getSimpleName().contentEquals("is" + capitalize(property.name()))
                        || method.getSimpleName().contentEquals("set" + capitalize(property.name()))) {
                    elements.add(method);
                }
            }
        }
        for (Element element : elements) {
            for (DeclaredType type : getClassValues(element, "value", excludeType)) {
                if (type.asElement() instanceof TypeElement annotationTypeElement) {
                    excludes.add(annotationTypeElement.getQualifiedName().toString());
                }
            }
            for (DeclaredType type : getClassValues(element, "value", ofType)) {
                if (type.asElement() instanceof TypeElement annotationTypeElement) {
                    includes.add(annotationTypeElement.getQualifiedName().toString());
                }
            }
        }
    }

    private List<VariableElement> declaredInstanceFields(TypeElement type) {
        return ElementFilter.fieldsIn(type.getEnclosedElements()).stream()
                .filter(field -> !field.getModifiers().contains(Modifier.STATIC))
                .toList();
    }

    private List<VariableElement> instanceFields(TypeElement type) {
        List<VariableElement> fields = new ArrayList<>();
        TypeElement current = type;
        while (current != null && !current.getQualifiedName().contentEquals("java.lang.Object")) {
            fields.addAll(ElementFilter.fieldsIn(current.getEnclosedElements()).stream()
                    .filter(field -> !field.getModifiers().contains(Modifier.STATIC))
                    .toList());
            TypeMirror superclass = current.getSuperclass();
            current = superclass.getKind() == TypeKind.DECLARED
                    ? (TypeElement) ((DeclaredType) superclass).asElement()
                    : null;
        }
        return fields;
    }

    private List<ExecutableElement> allMethods(TypeElement type) {
        List<ExecutableElement> methods = new ArrayList<>();
        TypeElement current = type;
        while (current != null && !current.getQualifiedName().contentEquals("java.lang.Object")) {
            methods.addAll(ElementFilter.methodsIn(current.getEnclosedElements()));
            TypeMirror superclass = current.getSuperclass();
            current = superclass.getKind() == TypeKind.DECLARED
                    ? (TypeElement) ((DeclaredType) superclass).asElement()
                    : null;
        }
        return methods;
    }

    private VariableElement findField(TypeElement type, String name) {
        return instanceFields(type).stream()
                .filter(field -> field.getSimpleName().contentEquals(name))
                .findFirst()
                .orElse(null);
    }

    private boolean methodExists(List<ExecutableElement> methods, String methodName) {
        return methods.stream().anyMatch(method -> method.getSimpleName().contentEquals(methodName));
    }

    private boolean isClassOrRecord(Element element) {
        return element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.RECORD;
    }

    private boolean isPrimitive(TypeMirror type) {
        return type.getKind().isPrimitive();
    }

    private boolean hasLombokAllArgsConstructor(TypeElement type) {
        return hasAnnotation(type, "lombok.AllArgsConstructor") || hasAnnotation(type, "lombok.Value");
    }

    private boolean hasAnnotation(Element element, String annotationName) {
        return element.getAnnotationMirrors().stream()
                .anyMatch(annotation -> annotation.getAnnotationType().toString().equals(annotationName));
    }

    private String propertyNameFromReader(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return decapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2
                && methodName.substring(2, 3).equals(methodName.substring(2, 3).toUpperCase())) {
            return decapitalize(methodName.substring(2));
        }
        return null;
    }

    private String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String decapitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private Optional<AnnotationMirror> getAnnotationMirror(Element element, Class<?> annotationType) {
        String annotationName = annotationType.getCanonicalName();
        for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
            if (annotation.getAnnotationType().toString().equals(annotationName)) {
                return Optional.of(annotation);
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private List<DeclaredType> getClassValues(Element element,
                                              String key,
                                              Class<? extends Annotation> annotationType) {
        return getAnnotationMirror(element, annotationType)
                .flatMap(annotation -> annotation.getElementValues().entrySet().stream()
                        .filter(entry -> entry.getKey().getSimpleName().contentEquals(key))
                        .map(Map.Entry::getValue)
                        .findFirst())
                .map(value -> (List<AnnotationValue>) value.getValue())
                .map(values -> values.stream()
                        .map(annotationValue -> (DeclaredType) annotationValue.getValue())
                        .toList())
                .orElse(List.of());
    }

    private record AnnotationConfig(
            Class<? extends Annotation> annotationType,
            Class<? extends Annotation> excludeType,
            Class<? extends Annotation> ofType,
            String methodName,
            boolean copyNotNull,
            boolean copyFrom
    ) {
    }

    private record PropertyInfo(
            String name,
            TypeMirror type,
            VariableElement field,
            String reader,
            String writer,
            boolean finalField,
            boolean publicField
    ) {
    }
}
