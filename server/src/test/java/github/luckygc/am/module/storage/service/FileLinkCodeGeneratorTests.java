package github.luckygc.am.module.storage.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("文件短链短码生成")
class FileLinkCodeGeneratorTests {

    @Test
    @DisplayName("默认生成 22 位字母数字短码")
    void generateShouldReturnTwentyTwoAlphanumericCharacters() {
        FileLinkCodeGenerator generator = new FileLinkCodeGenerator();

        String code = generator.generate();

        assertThat(code).hasSize(22).matches("[A-Za-z0-9]+");
    }

    @Test
    @DisplayName("连续生成短码不应重复")
    void generateShouldReturnDifferentCodes() {
        FileLinkCodeGenerator generator = new FileLinkCodeGenerator();

        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }
}
