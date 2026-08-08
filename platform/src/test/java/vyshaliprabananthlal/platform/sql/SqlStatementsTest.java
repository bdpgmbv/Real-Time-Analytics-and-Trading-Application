package vyshaliprabananthlal.platform.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SqlStatementsTest {

    private final SqlStatements statements = new SqlStatements();

    @Test
    @DisplayName("a statement is read from the file named after it")
    void aStatementIsReadFromItsFile() {
        assertThat(statements.statement("a-test-statement")).isEqualTo("SELECT 1");
    }

    @Test
    @DisplayName("surrounding blank lines are trimmed, so the statement starts where it should")
    void blankLinesAreTrimmed() {
        assertThat(statements.statement("a-test-statement"))
                .doesNotStartWith("\n")
                .doesNotEndWith("\n");
    }

    @Test
    @DisplayName("asking twice gives the same text without reading the file again")
    void askingTwiceIsTheSame() {
        assertThat(statements.statement("a-test-statement")).isEqualTo(statements.statement("a-test-statement"));
    }

    @Test
    @DisplayName("a statement that does not exist fails at once, naming the path it looked for")
    void aMissingStatementNamesThePath() {
        assertThatThrownBy(() -> statements.statement("no-such-statement"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sql/no-such-statement.sql");
    }
}
