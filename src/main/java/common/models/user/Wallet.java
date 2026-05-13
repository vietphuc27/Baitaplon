package common.models.user;

public class Wallet {
    private double balance;
    public Wallet(){
        this.balance = 0;
    }
    public synchronized boolean deposit(double amount){
        if (amount > 0){
            this.balance += amount;
            return true;
        }
        return false;
    }
    public synchronized boolean withdraw(double amount){
        if (amount > 0 && amount <= this.balance){
            this.balance -= amount;
            return true;
        }
        return false;
    }
    public double getBalance() {
        return balance;
    }

    public synchronized void setBalance(double balance) {
        this.balance = Math.max(0, balance);
    }
}
