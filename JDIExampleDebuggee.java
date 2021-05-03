public class JDIExampleDebuggee {

    public static void main(String[] args) throws Exception {
        Runnable run1 = new Run1();
        Runnable run2 = new Run2();
        Runnable run3 = new Run3();
        Thread mthread1 = new Thread(run1);
        Thread mthread2 = new Thread(run2);
        Thread mthread3 = new Thread(run3);
        mthread1.start();
        mthread2.start();
        mthread3.start();
    }
}
