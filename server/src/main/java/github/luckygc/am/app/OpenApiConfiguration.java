package github.luckygc.am.app;

import java.math.BigDecimal;
import java.util.Arrays;

import jakarta.data.page.PageRequest;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;

@Configuration
@OpenAPIDefinition(info = @Info(title = "档案管理 API", version = "0.0.1"))
public class OpenApiConfiguration {

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
            }
            return operation;
        };
    }
}
