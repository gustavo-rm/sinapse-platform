package br.com.sinapse.platform.datarights.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.sinapse.platform.datarights.api.TeacherAccessPeriod;
import br.com.sinapse.platform.datarights.internal.service.AccessDisclosureService;
import br.com.sinapse.platform.datarights.internal.service.PersonalDataExportService;
import br.com.sinapse.platform.datarights.support.DataRightsIntegrationTest;
import br.com.sinapse.platform.educational.api.ClassroomView;
import br.com.sinapse.platform.educational.api.EnrollmentView;
import br.com.sinapse.platform.identity.internal.domain.Account;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The two rights that are answered by reading rather than by removing: access, and information
 * about who the data was shared with.
 */
class ExportAndDisclosureIntegrationTest extends DataRightsIntegrationTest {

    @Autowired
    private PersonalDataExportService exports;

    @Autowired
    private AccessDisclosureService disclosure;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void theExportCarriesWhatEveryModuleHolds() {
        Account student = fullyPopulatedStudent();

        PersonalDataExportService.PersonalDataExport export = exports.exportFor(student.id());

        assertThat(export.generatedAt()).isNotNull();
        assertThat(export.modules())
                .as("assembled from each module rather than written here; the coordinator never "
                        + "learns what a study session is")
                .containsKeys("identity", "learningrecord", "planning", "educational");

        assertThat(collection(export, "identity", "account")).hasSize(1);
        assertThat(collection(export, "identity", "consents")).isNotEmpty();
        assertThat(collection(export, "learningrecord", "studySessions")).isNotEmpty();
        assertThat(collection(export, "planning", "availability")).isNotEmpty();
        assertThat(collection(export, "planning", "goals")).isNotEmpty();
        assertThat(collection(export, "planning", "studyPlans")).isNotEmpty();
        assertThat(collection(export, "planning", "plannedSessions")).isNotEmpty();
        assertThat(collection(export, "planning", "generationRequests")).isNotEmpty();
        assertThat(collection(export, "educational", "enrollments")).isNotEmpty();
    }

    @Test
    void theExportSerialisesAndCarriesNoCredential() throws Exception {
        Account student = fullyPopulatedStudent();
        String hash = accountColumn(student.id(), "password_hash", String.class);

        String json = objectMapper.writeValueAsString(exports.exportFor(student.id()));

        assertThat(json)
                .as("the holder's own address is theirs and belongs in it")
                .contains(student.email());
        assertThat(json)
                .as("a password hash is not the holder's data in any useful sense, and an export "
                        + "that carried it would put an offline-crackable credential in a file "
                        + "the holder is invited to keep")
                .doesNotContain(hash)
                .doesNotContain("passwordHash");
        assertThat(json)
                .as("the snapshot is a derived copy of everything else in this same document; "
                        + "duplicating the most sensitive artifact in the system into a download "
                        + "would tell the holder nothing new")
                .doesNotContain("\"snapshot\"");
    }

    @Test
    void anEmptyAccountExportsEmptyCollectionsRatherThanNothing() {
        Account student = student();

        PersonalDataExportService.PersonalDataExport export = exports.exportFor(student.id());

        assertThat(export.modules()).containsKeys("identity", "learningrecord", "planning",
                "educational");
        assertThat(collection(export, "learningrecord", "studySessions")).isEmpty();
        assertThat(collection(export, "identity", "account"))
                .as("a holder with nothing recorded still has an account, and it is theirs")
                .hasSize(1);
    }

    @Test
    void theDisclosureNamesTheTeachersWhoCouldReadTheData() {
        Account student = fullyPopulatedStudent();
        ClassroomView classroom = joinAClassroom(student);

        List<TeacherAccessPeriod> periods = disclosure.disclosureFor(student.id());

        assertThat(periods).hasSize(2);
        assertThat(periods).anySatisfy(period -> {
            assertThat(period.classroomId()).isEqualTo(classroom.id());
            assertThat(period.classroomName()).isEqualTo(classroom.name());
            assertThat(period.teacherName())
                    .as("a membership is the only thing that grants a teacher access, so it is "
                            + "the only thing a disclosure can be derived from")
                    .isEqualTo("Prof. Exemplo");
            assertThat(period.institution()).isEqualTo("Universidade de Exemplo");
            assertThat(period.from()).isNotNull();
            assertThat(period.isOpen()).isTrue();
        });
    }

    @Test
    void aClosedMembershipIsDisclosedWithItsEndAndStaysAfterErasure() {
        Account student = fullyPopulatedStudent();
        EnrollmentView membership = educationalDirectory().enrollmentsOf(student.id()).getFirst();
        classrooms.leave(student.id(), membership.id());

        assertThat(disclosure.disclosureFor(student.id()))
                .as("who could see the holder's data, and until when")
                .anySatisfy(period -> {
                    assertThat(period.to()).isNotNull();
                    assertThat(period.isOpen()).isFalse();
                });

        eraseNow(student);

        assertThat(disclosure.disclosureFor(student.id()))
                .as("the enrollments survive the erasure, so the account of who had access "
                        + "survives with them")
                .isNotEmpty();
    }

    @Test
    void aStudentWithNoMembershipsHasNothingToDisclose() {
        assertThat(disclosure.disclosureFor(student().id())).isEmpty();
    }

    private static List<Object> collection(PersonalDataExportService.PersonalDataExport export,
            String module, String name) {

        Map<String, List<Object>> collections = export.modules().get(module);
        assertThat(collections).as("module %s is missing from the export", module).isNotNull();
        assertThat(collections).as("collection %s is missing from module %s", name, module)
                .containsKey(name);
        return collections.get(name);
    }

    private br.com.sinapse.platform.educational.api.EducationalDirectory educationalDirectory() {
        return educational;
    }
}
