package github.luckygc.am.app;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.data.page.PageRequest;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI openAPI() {
        ObjectSchema problem = new ObjectSchema();
        problem.setProperties(
                Map.of(
                        "type", new StringSchema().format("uri"),
                        "title", new StringSchema(),
                        "status", new IntegerSchema(),
                        "detail", new StringSchema(),
                        "instance", new StringSchema().format("uri")));
        problem.setRequired(List.of("type", "title", "status", "detail"));
        problem.setAdditionalProperties(true);
        return new OpenAPI()
                .info(new Info().title("档案管理 API").version("0.0.1"))
                .components(new Components().addSchemas("ProblemDetail", problem));
    }

    @Bean
    OperationCustomizer projectApiContract() {
        return (operation, handlerMethod) -> {
            boolean cursorPaged =
                    Arrays.stream(handlerMethod.getMethodParameters())
                            .anyMatch(
                                    parameter ->
                                            PageRequest.class.equals(parameter.getParameterType()));
            if (cursorPaged) {
                if (operation.getParameters() != null) {
                    operation
                            .getParameters()
                            .removeIf(
                                    parameter -> {
                                        String ref =
                                                parameter.getSchema() == null
                                                        ? null
                                                        : parameter.getSchema().get$ref();
                                        return ref != null && ref.endsWith("/PageRequest");
                                    });
                }
                IntegerSchema limit = new IntegerSchema();
                limit.setDefault(100);
                limit.setMinimum(BigDecimal.ONE);
                limit.setMaximum(BigDecimal.valueOf(1000));
                operation.addParametersItem(
                        new Parameter()
                                .name("limit")
                                .in("query")
                                .description("每页条数，默认 100，最大 1000")
                                .schema(limit));
                operation.addParametersItem(
                        new Parameter()
                                .name("cursor")
                                .in("query")
                                .description("上一页或下一页响应给出的不透明游标")
                                .schema(new StringSchema()));
            }

            Class<?> responseType = handlerMethod.getReturnType().getParameterType();
            if (ResponseEntity.class.isAssignableFrom(responseType)) {
                responseType =
                        ResolvableType.forMethodReturnType(handlerMethod.getMethod())
                                .getGeneric(0)
                                .resolve(Object.class);
            }
            boolean binaryResponse = Resource.class.isAssignableFrom(responseType);
            if (operation.getResponses() != null) {
                operation
                        .getResponses()
                        .values()
                        .forEach(
                                response -> {
                                    Content content = response.getContent();
                                    if (content != null && content.containsKey("*/*")) {
                                        MediaType media = content.remove("*/*");
                                        if (binaryResponse) {
                                            media =
                                                    new MediaType()
                                                            .schema(
                                                                    new StringSchema()
                                                                            .format("binary"));
                                        }
                                        content.addMediaType(
                                                binaryResponse
                                                        ? "application/octet-stream"
                                                        : "application/json",
                                                media);
                                    }
                                });
            } else {
                operation.setResponses(new ApiResponses());
            }
            operation
                    .getResponses()
                    .putIfAbsent(
                            "default",
                            new ApiResponse()
                                    .description("错误按实际 HTTP 状态返回 RFC 9457 ProblemDetail")
                                    .content(
                                            new Content()
                                                    .addMediaType(
                                                            "application/problem+json",
                                                            new MediaType()
                                                                    .schema(
                                                                            new Schema<>()
                                                                                    .$ref(
                                                                                            "#/components/schemas/ProblemDetail")))));
            return operation;
        };
    }
}
