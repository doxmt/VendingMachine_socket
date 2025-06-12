package model;

public class Money {
    private int won10Count;
    private int won50Count;
    private int won100Count;
    private int won500Count;
    private int won1000Count;
    private int won5000Count;

    public Money() {
        this.won10Count = 10;
        this.won50Count = 10;
        this.won100Count = 10;
        this.won500Count = 10;
        this.won1000Count = 10;
        this.won5000Count = 10;
    }

    public int get10WonCount() {
        return won10Count;
    }

    public int get50WonCount() {
        return won50Count;
    }

    public int get100WonCount() {
        return won100Count;
    }

    public int get500WonCount() {
        return won500Count;
    }

    public int get1000WonCount() {
        return won1000Count;
    }

    public int get5000WonCount() {
        return won5000Count;
    }

    public void add10Won(int count) {
        this.won10Count += count;
    }

    public void add50Won(int count) {
        this.won50Count += count;
    }

    public void add100Won(int count) {
        this.won100Count += count;
    }

    public void add500Won(int count) {
        this.won500Count += count;
    }

    public void add1000Won(int count) {
        this.won1000Count += count;
    }

    public void add5000Won(int count) {
        this.won5000Count += count;
    }

    public void use10Won(int count) {
        if (this.won10Count >= count) {
            this.won10Count -= count;
        }
    }

    public void use50Won(int count) {
        if (this.won50Count >= count) {
            this.won50Count -= count;
        }
    }

    public void use100Won(int count) {
        if (this.won100Count >= count) {
            this.won100Count -= count;
        }
    }

    public void use500Won(int count) {
        if (this.won500Count >= count) {
            this.won500Count -= count;
        }
    }

    public void use1000Won(int count) {
        if (this.won1000Count >= count) {
            this.won1000Count -= count;
        }
    }

    public void use5000Won(int count) {
        if (this.won5000Count >= count) {
            this.won5000Count -= count;
        }
    }

    public int getTotalAmount() {
        return won10Count * 10 + won50Count * 50 + won100Count * 100 + won500Count * 500 + 
        		won1000Count * 1000 + won5000Count * 5000;
    }

    public int collect10Won() {
        int collected = this.won10Count - 10;
        this.won10Count = 10;
        return collected * 10;
    }

    public int collect50Won() {
        int collected = this.won50Count - 10;
        this.won50Count = 10;
        return collected * 50;
    }

    public int collect100Won() {
        int collected = this.won100Count - 10;
        this.won100Count = 10;
        return collected * 100;
    }

    public int collect500Won() {
        int collected = this.won500Count - 10;
        this.won500Count = 10;
        return collected * 500;
    }

    public int collect1000Won() {
        int collected = this.won1000Count - 10;
        this.won1000Count = 10;
        return collected * 1000;
    }

    public int collect5000Won() {
        int collected = this.won5000Count - 10;
        this.won5000Count = 10;
        return collected * 5000;
    }
}
