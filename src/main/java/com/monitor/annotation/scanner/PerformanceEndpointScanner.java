/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.scanner;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.monitor.annotation.annotation.PerformanceMeasure;
import com.monitor.annotation.dto.PerformanceEndpoint;
import java.lang.reflect.Parameter;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring 애플리케이션에서 @PerformanceMeasure 어노테이션이 적용된 엔드포인트 및 관련 서비스 메서드 스캔
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceEndpointScanner {

    private final ApplicationContext applicationContext;
    private volatile Map<String, List<PerformanceEndpoint>> cachedEndpoints;

    // 소스 파일 캐시 (반복적인 파일 시스템 접근 방지)
    private final Map<String, String> sourceFileCache = new ConcurrentHashMap<>();


    /**
     * 애플리케이션의 모든 엔드포인트와 연관된 서비스 메서드 스캔
     *
     * @return HTTP 메서드별로 그룹화된 엔드포인트 정보
     */
    public Map<String, List<PerformanceEndpoint>> scanEndpoints() {
        if (cachedEndpoints == null) {
            synchronized (this) {
                if (cachedEndpoints == null) {
                    cachedEndpoints = doScanEndpoints();
                }
            }
        }
        return new HashMap<>(cachedEndpoints);
    }

    private Map<String, List<PerformanceEndpoint>> doScanEndpoints() {
        Map<String, List<PerformanceEndpoint>> endpointMap = new HashMap<>();

        try {
            Map<String, Object> controllers = new HashMap<>();
            controllers.putAll(applicationContext.getBeansWithAnnotation(RestController.class));
            controllers.putAll(applicationContext.getBeansWithAnnotation(Controller.class));

            log.info("Controller canning tart: {} controllers found", controllers.size());

            for (Object controller : controllers.values()) {
                Class<?> controllerClass = controller.getClass();
                String baseUrl = getControllerBaseUrl(controllerClass);

                // AOP 프록시인 경우 실제 클래스 가져오기
                if (controllerClass.getSimpleName().contains("$$")) {
                    controllerClass = controllerClass.getSuperclass();
                }

                log.info("Processing controllers: {}", controllerClass.getSimpleName());

                for (Method method : controllerClass.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(PerformanceMeasure.class)) {
                        String url = getMethodUrl(method, baseUrl);
                        String httpMethod = getHttpMethod(method);

                        if (url != null && httpMethod != null) {
                            PerformanceEndpoint endpoint = createEndpoint(controllerClass, method,
                                url, httpMethod);
                            endpointMap.computeIfAbsent(httpMethod, k -> new ArrayList<>())
                                .add(endpoint);
                            log.info("Endpoint Added: {} {}", httpMethod, url);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error occurred while scanning", e);
        }

        return endpointMap;
    }

    /**
     * 컨트롤러의 기본 URL 추출
     */
    private String getControllerBaseUrl(Class<?> controllerClass) {
        // Spring CGLIB 프록시인 경우 실제 클래스 가져옴
        if (controllerClass.getSimpleName().contains("$$")) {
            controllerClass = controllerClass.getSuperclass();
        }

        String baseUrl = "";

        // RequestMapping 어노테이션 확인
        RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
        if (requestMapping != null && requestMapping.value().length > 0) {
            baseUrl = requestMapping.value()[0];
        }

        // path 속성도 확인
        if (baseUrl.isEmpty() && requestMapping != null && requestMapping.path().length > 0) {
            baseUrl = requestMapping.path()[0];
        }

        log.debug("Controller: {}, BaseUrl: {}", controllerClass.getSimpleName(), baseUrl);
        return baseUrl;
    }

    /**
     * 메서드의 URL 패턴 추출
     */
    private String getMethodUrl(Method method, String baseUrl) {
        String methodUrl = "";

        // HTTP 메서드 매핑 어노테이션들을 처리
        if (method.isAnnotationPresent(RequestMapping.class)) {
            methodUrl = getUrlFromAnnotation(method.getAnnotation(RequestMapping.class).value());
        } else if (method.isAnnotationPresent(GetMapping.class)) {
            methodUrl = getUrlFromAnnotation(method.getAnnotation(GetMapping.class).value());
        } else if (method.isAnnotationPresent(PostMapping.class)) {
            methodUrl = getUrlFromAnnotation(method.getAnnotation(PostMapping.class).value());
        } else if (method.isAnnotationPresent(PutMapping.class)) {
            methodUrl = getUrlFromAnnotation(method.getAnnotation(PutMapping.class).value());
        } else if (method.isAnnotationPresent(DeleteMapping.class)) {
            methodUrl = getUrlFromAnnotation(method.getAnnotation(DeleteMapping.class).value());
        } else if (method.isAnnotationPresent(PatchMapping.class)) {
            methodUrl = getUrlFromAnnotation(method.getAnnotation(PatchMapping.class).value());
        }

        // URL 중복 슬래시 방지
        String combinedUrl = "";
        if (baseUrl.endsWith("/") && methodUrl.startsWith("/")) {
            // baseUrl이 /로 끝나고 methodUrl이 /로 시작하면 하나 제거
            combinedUrl = baseUrl + methodUrl.substring(1);
        } else if (!baseUrl.endsWith("/") && !methodUrl.startsWith("/") && !methodUrl.isEmpty()) {
            // 둘 다 /가 없는 경우 /를 추가
            combinedUrl = baseUrl + "/" + methodUrl;
        } else {
            // 그 외의 경우는 그대로 결합
            combinedUrl = baseUrl + methodUrl;
        }

        // 시작이 /가 아닌 경우 추가
        if (!combinedUrl.startsWith("/")) {
            combinedUrl = "/" + combinedUrl;
        }

        return combinedUrl;
    }

    /**
     * 어노테이션 value 배열에서 URL 추출
     */
    private String getUrlFromAnnotation(String[] values) {
        if (values == null || values.length == 0) {
            return "";
        }
        return values[0];
    }

    /**
     * HTTP 메서드 타입 추출
     */
    private String getHttpMethod(Method method) {
        if (method.isAnnotationPresent(GetMapping.class)) {
            return "GET";
        }
        if (method.isAnnotationPresent(PostMapping.class)) {
            return "POST";
        }
        if (method.isAnnotationPresent(PutMapping.class)) {
            return "PUT";
        }
        if (method.isAnnotationPresent(DeleteMapping.class)) {
            return "DELETE";
        }
        if (method.isAnnotationPresent(PatchMapping.class)) {
            return "PATCH";
        }

        if (method.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping mapping = method.getAnnotation(RequestMapping.class);
            if (mapping.method().length > 0) {
                return mapping.method()[0].name();
            }
        }

        return null;
    }

    /**
     * JavaParser를 사용하여 메서드 내의 모든 메서드 호출 찾기
     */
    private Set<String> findMethodCalls(Method method) {
        Set<String> methodCalls = new HashSet<>();
        try {
            String sourceFile = findSourceFile(method.getDeclaringClass().getSimpleName());
            if (sourceFile != null) {
                CompilationUnit cu = StaticJavaParser.parse(new File(sourceFile));

                cu.findAll(MethodDeclaration.class).stream()
                    .filter(md -> md.getNameAsString().equals(method.getName()))
                    .forEach(md -> {
                        md.findAll(MethodCallExpr.class).forEach(call ->
                            methodCalls.add(call.getNameAsString()));
                    });
            }
        } catch (Exception e) {
            log.warn("Could not parse source file for method: " + method.getName(), e);
        }
        return methodCalls;
    }

    /**
     * 클래스의 소스 파일 찾기
     */
    private String findSourceFile(String className) {
        return sourceFileCache.computeIfAbsent(className, this::locateSourceFile);
    }

    /**
     * 실제 소스 파일 위치 찾기
     */
    private String locateSourceFile(String className) {
        try {
            // 클래스 로더에서 클래스 파일 위치 찾기
            String classFile = className + ".class";
            URL classUrl = getClass().getClassLoader().getResource(classFile);
            if (classUrl == null) {
                log.warn("Could not find class file for: {}", className);
                return null;
            }

            // 클래스 파일 경로를 소스 파일 경로로 변환
            String classPath = classUrl.getPath();
            Path sourcePath = convertClassPathToSourcePath(classPath, className);

            if (sourcePath != null && Files.exists(sourcePath)) {
                return sourcePath.toString();
            }

            // 일반적인 소스 디렉토리들을 탐색
            String[] commonSourceDirs = {
                "src/main/java",
                "src/test/java"
            };

            String relativeSourcePath = className.replace('.', '/') + ".java";
            for (String sourceDir : commonSourceDirs) {
                Path possiblePath = Paths.get(sourceDir, relativeSourcePath);
                if (Files.exists(possiblePath)) {
                    return possiblePath.toString();
                }
            }

            log.warn("Could not find source file for class: {}", className);
            return null;

        } catch (Exception e) {
            log.error("Error finding source file for class: " + className, e);
            return null;
        }
    }

    /**
     * 클래스 파일 경로를 소스 파일 경로로 변환
     */
    private Path convertClassPathToSourcePath(String classPath, String className) {
        try {
            Path path = Paths.get(classPath);
            String sourceRoot = findSourceRoot(path);

            if (sourceRoot != null) {
                return Paths.get(sourceRoot, className.replace('.', '/') + ".java");
            }
        } catch (Exception e) {
            log.warn("Error converting class path to source path", e);
        }

        return null;
    }

    /**
     * 프로젝트의 소스 루트 디렉토리 찾기
     */
    private String findSourceRoot(Path classPath) {
        try {
            Path current = classPath;
            while (current != null) {
                if (Files.exists(current.resolve("src/main/java"))) {
                    return current.resolve("src/main/java").toString();
                }
                if (Files.exists(current.resolve("build.gradle")) ||
                    Files.exists(current.resolve("pom.xml"))) {
                    return current.resolve("src/main/java").toString();
                }
                current = current.getParent();
            }
        } catch (Exception e) {
            log.warn("Error finding source root", e);
        }
        return null;
    }

    /**
     * 엔드포인트 정보 생성
     */
    private PerformanceEndpoint createEndpoint(Class<?> controllerClass, Method method,
        String url, String httpMethod) {

        // @PerformanceMeasure 어노테이션에서 설명 가져오기
        String description = Optional.ofNullable(method.getAnnotation(PerformanceMeasure.class))
            .map(PerformanceMeasure::value)
            .orElse("");

        // 요청 바디 타입 분석
        Class<?> requestBodyType;
        Optional<Parameter> bodyParam = Arrays.stream(method.getParameters())
            .filter(param -> param.isAnnotationPresent(RequestBody.class))
            .findFirst();

        if (bodyParam.isPresent()) {
            requestBodyType = bodyParam.get().getType();
        } else {
            requestBodyType = Void.class;
        }

        // 요청 예시 생성
        Map<String, String> requestExample = new HashMap<>();
        if (requestBodyType != void.class) {
            requestExample = generateRequestExample(requestBodyType);
        }

        // URL 파라미터 분석
        Map<String, String> parameters = Arrays.stream(method.getParameters())
            .filter(param -> param.isAnnotationPresent(RequestParam.class) ||
                param.isAnnotationPresent(PathVariable.class))
            .collect(Collectors.toMap(
                Parameter::getName,
                param -> param.getType().getSimpleName()
            ));

        return PerformanceEndpoint.builder()
            .endpointUrl(url)
            .httpMethod(httpMethod)
            .controllerClassName(controllerClass.getSimpleName())
            .controllerMethodName(method.getName())
            .requestType(requestBodyType.getSimpleName())
            .responseType(method.getReturnType().getSimpleName())
            .description(description)
            .parameters(parameters)
            .requestExample(requestExample)
            .annotatedServices(new ArrayList<>())
            .build();
    }

    private Map<String, String> generateRequestExample(Class<?> type) {
        Map<String, String> example = new LinkedHashMap<>();  // 순서 유지를 위해 LinkedHashMap 사용

        // DTO 클래스인 경우 필드별 예시 값 생성
        if (type.getAnnotation(Data.class) != null ||
            type.getAnnotation(Getter.class) != null) {  // Lombok 어노테이션 체크

            example.put("// Description", "\"" + type.getSimpleName() + " Request Body Format\"");

            // 필드별 예시 값 생성
            for (Field field : type.getDeclaredFields()) {
                String fieldType = field.getType().getSimpleName();
                String exampleValue = switch (fieldType.toLowerCase()) {
                    case "string" -> "example";
                    case "int", "integer" -> "0";
                    case "long" -> "0";
                    case "boolean" -> "false";
                    case "double", "float" -> "0.0";
                    case "localdate" -> "\"2024-01-02\"";
                    case "localdatetime" -> "\"2024-01-02T09:00:00\"";
                    case "list" -> "[]";
                    case "map" -> "{}";
                    default -> "null";
                };
                example.put(field.getName(), exampleValue);
            }

            // 필드 타입 정보 주석
            example.put("// Field Types", "{" +
                Arrays.stream(type.getDeclaredFields())
                    .map(
                        field -> "\"" + field.getName() + "\": \"" + field.getType().getSimpleName()
                            + "\"")
                    .collect(Collectors.joining(", ")) +
                "}");
        }

        return example;
    }

    /**
     * 요청 타입 추출 (@RequestBody 파라미터 타입)
     */
    private String getRequestType(Method method) {
        return Arrays.stream(method.getParameters())
            .filter(param -> param.isAnnotationPresent(RequestBody.class))
            .findFirst()
            .map(param -> param.getType().getSimpleName())
            .orElse("void");
    }
}