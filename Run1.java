public class Run1 implements Runnable{
    @Override
    public void run(){
        String str1 = "thread1, line1";
        System.out.println(str1);
        System.out.println("thread1, line2");
        int i=0;
        int j=1;
    }
}