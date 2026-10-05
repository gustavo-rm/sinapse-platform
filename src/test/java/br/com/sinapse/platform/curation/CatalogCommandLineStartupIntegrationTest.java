package br.com.sinapse.platform.curation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.PlatformApplication;
import br.com.sinapse.platform.curriculum.support.CurriculumIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * The importer started the way an operator starts it: the whole application, under the
 * {@code catalog} profile, with {@code catalog apply} on the command line.
 *
 * <p>Every other importer test builds {@code CatalogRunner} by hand, which is exactly what let
 * the class reach a release with a constructor Spring could not choose: the unit under test
 * worked, and the command did not start. This one leaves the construction to Spring.
 */
class CatalogCommandLineStartupIntegrationTest extends CurriculumIntegrationTest {

    @TempDir
    Path catalog;

    @Autowired
    private Environment environment;

    @Test
    void catalogApplyStartsUnderTheCatalogProfileAndWritesTheCatalogue() throws Exception {
        Path subject = Files.createDirectories(catalog.resolve("CLI-SUB"));
        Files.writeString(subject.resolve("subject.csv"), "name\nDisciplina de linha de comando\n",
                StandardCharsets.UTF_8);
        Files.writeString(subject.resolve("topics.csv"), """
                code,name,position,effort_tier
                primeiro,Primeiro tópico,1,SHORT
                segundo,Segundo tópico,2,STANDARD
                """, StandardCharsets.UTF_8);
        Files.writeString(subject.resolve("prerequisites.csv"), """
                prerequisite,dependent,strength,provenance,source_reference
                CLI-SUB:primeiro,CLI-SUB:segundo,HARD,CURATED,
                """, StandardCharsets.UTF_8);

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                PlatformApplication.class)
                .profiles("catalog")
                .run("catalog", "apply",
                        "--sinapse.catalog.directory=" + catalog,
                        "--spring.datasource.url=" + environment.getProperty("spring.datasource.url"),
                        "--spring.datasource.username="
                                + environment.getProperty("spring.datasource.username"),
                        "--spring.datasource.password="
                                + environment.getProperty("spring.datasource.password"))) {

            assertThat(SpringApplication.exit(context)).as("the command's exit code").isZero();
        }

        assertThat(jdbc.queryForObject("select name from subject where code = 'CLI-SUB'",
                String.class)).isEqualTo("Disciplina de linha de comando");
        assertThat(jdbc.queryForObject("""
                select count(*) from topic t join subject s on s.id = t.subject_id
                 where s.code = 'CLI-SUB'
                """, Integer.class)).isEqualTo(2);
        assertThat(edgeCount()).isEqualTo(1);
    }
}
