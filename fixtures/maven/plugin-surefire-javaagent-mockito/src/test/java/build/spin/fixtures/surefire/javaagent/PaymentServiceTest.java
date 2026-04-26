package build.spin.fixtures.surefire.javaagent;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    @Test
    void chargeSucceedsWhenSufficientFunds() {
        Repository repo = Mockito.mock(Repository.class);
        when(repo.getBalance("acct1")).thenReturn(100);

        assertTrue(new PaymentService(repo).charge("acct1", 50));
        verify(repo).debit("acct1", 50);
    }

    @Test
    void chargeFailsWhenInsufficientFunds() {
        Repository repo = Mockito.mock(Repository.class);
        when(repo.getBalance("acct2")).thenReturn(10);

        assertFalse(new PaymentService(repo).charge("acct2", 50));
    }
}
