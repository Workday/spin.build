package build.spin.fixtures.surefire.javaagent;

public interface Repository {
    int getBalance(String accountId);
    void debit(String accountId, int amount);
}
