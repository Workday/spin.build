package build.spin.fixtures.surefire.javaagent;

public class PaymentService {

    private final Repository repository;

    public PaymentService(Repository repository) {
        this.repository = repository;
    }

    public boolean charge(String accountId, int amount) {
        int balance = repository.getBalance(accountId);
        if (balance < amount) return false;
        repository.debit(accountId, amount);
        return true;
    }
}
