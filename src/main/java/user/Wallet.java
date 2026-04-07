package user;

public class Wallet {
    private double balance;
    public Wallet(){
        this.balance = 0;
    }
    public boolean deposit(double amount){
        if (amount > 0){
            this.balance += amount;
            return true;
        }
        return false;
    }
    public boolean withdraw(double amount){
        if (amount > 0 && amount <= this.balance){
            this.balance -= amount;
            return true;
        }
        return false;
    }
    public double getBalance() {
        return balance;
    }
}
