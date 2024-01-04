import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;//用来创建线程池
import java.util.concurrent.ThreadFactory;//给thread设置id
import java.util.concurrent.TimeUnit;//定时任务
import java.util.HashMap;
import java.util.Hashtable;//线程安全
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

/*我的想法是，做一个waitting list class，在里面放一个可以检查下一个应该被check 是否存在pattern的指针
因为第二部分是生产者和消费者问题，题目要求存在2到5个消费者线程
消费者线程存在的意义是同时对多个node进行处理，所以node加入linked list和检查下一个需要被check pattern的 node需要线程安全
但是调用node本身的search pattern方法不需要，就是说要对每一个node对象同时进行pattern search
*/
public class Assignment3 {
    private static List<Node> sharedList = new ArrayList<>();// part1 里的list
    private static List<Node> frequentList = new ArrayList<>();// 因为part1里面add to list方法是线程安全的，所以每次只要检查line是否contains
                                                               // pattern就可以直接加入frequentlist
    private static Hashtable<Integer, Integer> countMap = new Hashtable<>();// hashtable
                                                                            // 线程安全的，存健值对，每本书在5秒钟内得到多少frequency
    private static int bookCounter = 1;
    private static String pattern;// 要找的关键字
    private static int clientNumber = 0;// 让client全退出之后关闭线程池用的
    private volatile static boolean flag = false;// 检测是否有消费者，在rewrite run开头
    private volatile static boolean hasClient = false;// 配合关闭线程池
    private volatile static boolean quit = false;// 检查是否全部的生产者已经退出critical section
    volatile static boolean enterPrint = false;
    static WaitingList waitingList = new WaitingList();// 在5秒钟内处理的全部node都会被加入到waiting list里面

    
    private static synchronized void addToSharedList(String line, int bookId) {
        Node node = new Node(line, sharedList.size() + 1, bookId);// sharelist。size是为了给node上sequence
                                                                  // number虽然没有用到，bookid是为了得到5秒内frequent到底对应那本书
        while (!flag == false)// 如果有消费者
        {
            if (quit == false)// 告诉消费者我不在critical section
            {
                quit = true;
                
            }
            // busy waiting
        }
        if (quit == true) 
        {
            quit = false;
        }
        waitingList.add(node);// 全部node加入waiting list无序
        if (line.contains(pattern)) {
            frequentList.add(node);// 有序的next frequency list但是没用到
            // 其实可以直接用这个frequentlist来弄，直接去查里面的node的frequency也可以
        }
        System.out.println("Added to shared list: " + line);// 第一部分的没改
        
    }

    public static class WaitingList// 自己定义的waiting list class
    {
        Node head;
        Node nextDealNode;// 对应的下一个要被检查的node

        WaitingList() {
            head = null;
            nextDealNode = null;
        }

        synchronized Node getNextSearch()// 找到下一个需要被check的node
        {
            if (head == null)// 如果没有，就返回fakenode，停止task1
            {
                Node fakeNode = new Node(true);// 这边是调用node里的一个attribute的建造方法，让fakeNode =true说明是fake node
                nextDealNode = fakeNode;
                return fakeNode;
            }
            if (nextDealNode == null)// nextDealNode的默认值是null所以第一次访问的时候可以返回head，并设置head。next为nextdealnode
            {
                if (head.next == null) {
                    Node fakeNode = new Node(true);
                    nextDealNode = fakeNode;
                }
                else
                {
                    nextDealNode = head.next;
                }
                return head;
            } 
            else// 返回nextdeal node ，设置新的next deal node
            {
                if (nextDealNode.fakeNode == true) 
                {
                    return nextDealNode;
                }
                Node currentDealNode = nextDealNode;
                nextDealNode = nextDealNode.next;
                return currentDealNode;
            }
        }

        synchronized void add(Node node) {
            if (node == null) {
                throw new IllegalArgumentException("Cannot add a null node.");
            }
            if (this.head == null) {
                head = node;
            } else {
                Node current = head;
                while (current.next != null) {
                    current = current.next;
                }
                current.setNext(node);
            }
        }

        synchronized void clear()// 每五秒清空这个工作队列
        {
            head = null;
            nextDealNode = null;
        }
    }

    private static class Node {
        String line;
        int sequenceNumber;// 这个本来是为了位置linkedlist的位置问题，就是每个线程在访问cotains（pattern）的时候，存在先后问题，为了保持次序，但是没有用到
        int bookId;// 这个是通过part 1里 b对象实现的
        boolean fakeNode = false;
        Node next = null;

        Node(Boolean fakeN) {
            fakeNode = fakeN;
        }

        Node(String line, int sequenceNumber, int bookId) {
            if (line == null) {
                throw new IllegalArgumentException("Node line cannot be null.");
            }
            this.line = line;
            this.sequenceNumber = sequenceNumber;
            this.bookId = bookId;
        }

        public void setNext(Node node) {
            this.next = node;
        }

        public synchronized int searchPattern()// 检查存在多少个pattern，看看有没有问题
        {
            if (!line.contains(pattern)) {
                return 0;
            }
            int count = 0;
            int index = line.indexOf(pattern);
            while (index != -1) {
                count++;
                index = line.indexOf(pattern, index + 1);
            }
            return count;
        }
    }

    private static class Book {
        List<String> bookLines;
        int connection;

        Book(List<String> bookLines, int connection) {
            this.bookLines = bookLines;
            this.connection = connection;
        }
    }

    private static synchronized void writeBookToFile(Book bo) {
        String fileName = "book_" + String.format("%02d", bo.connection) + ".txt";
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            for (String line : bo.bookLines) {
                writer.println(line);
            }
            System.out.println("Successfully writes " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static synchronized void clientPlus()// 为了线程安全的确认客户数量的方法
    {
        clientNumber += 1;
    }

    static class NumberedThreadFactory implements ThreadFactory// 为了给每一个线程池中的线程分配1开始的线程id
    {
        private int count = 1;

        @Override
        public Thread newThread(Runnable r) {
            // 创建新线程并设置名称
            Thread thread = new Thread(r, "Process-" + count++);
            return thread;
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                clientPlus();
                if (hasClient == false) 
                {
                    hasClient = true;
                    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);// 建立线程池，2表示会有两个线程
                    new NumberedThreadFactory();// 建立thread factory为了后面的线程id
                    Runnable analysisTask1 = () -> {
                        
                        if (flag == false) 
                        {
                            flag = true;
                        }
                        while (!quit == true) 
                        {
                            // busy waiting
                        }
                        if (enterPrint == true)
                        {
                            enterPrint = false;
                        }
                        int counterW = 1;
                        Node current = waitingList.head;
                        while(current.next!= null)
                        {
                            counterW++;
                            current = current.next;                
                        }
                        String threadName = Thread.currentThread().getName();// 线程id建立，为了打印用
                        String[] parts = threadName.split("-");
                        threadName = parts[3];
                        for(int n = 1;n<counterW;n++)
                        {
                            Node nextSearchNode = waitingList.getNextSearch();
                        
                            // 建立数据结构，对于node.bookid加入在hashtable，直到taskfinished
                            // 使用node.searchpattern方法，返回int即pattern出现的次数，计入arraylist对应
                            // 直到全部线程结束
                            boolean containKey = countMap.containsKey(nextSearchNode.bookId);
                            int num = nextSearchNode.searchPattern();
                            if (!containKey) {
                                countMap.put(nextSearchNode.bookId, num);
                            } else {
                                int revceivedNum = countMap.get(nextSearchNode.bookId);
                                num = num + revceivedNum;
                                countMap.put(nextSearchNode.bookId, num);
                            }
                        }
                        synchronized (System.out) 
                        {
                            try
                            {
                                if (enterPrint == true)    
                                {}
                                else
                                {
                                    enterPrint = true;
                                    ArrayList printList = new ArrayList();
                                    System.out.println("-----------------");
                                    System.out.println("| Analysis Thread " + threadName + ":");
                                    int countingZero = 0;
                                    for (int n = 1; n <countMap.size()+1;n++)
                                    {

                                        if (countMap.get(n)==0)
                                        {
                                            countingZero++;
                                        }
                                    }
                                    int forLoopCount = countMap.size() - countingZero;
                                    if (forLoopCount == 0)
                                    {
                                        for (int i = 1; i<countMap.size()+1;i++)
                                        {
                                            System.out.print("| ");
                                            System.out.println("Book " + i + ": 0 occurences");
                                        }
                                    }
                                    else
                                    {
                                        for (int i = 1; i<forLoopCount +1; i++)
                                        {
                                            System.out.print("| ");
                                            int currentBiggest = 0;
                                            int keyValue = 0;
                                            for (int j = 0; j < countMap.size(); j++)// 简单的找最大值打印
                                            {
                                                if (currentBiggest < countMap.get(j)) 
                                                {
                                                    currentBiggest = countMap.get(j);
                                                    keyValue = j;
                                                }
                                            }
                                            System.out.println("Book " + countMap.get(keyValue) + ":" + currentBiggest +" occurences");
                                        }
                                    } 
                                }

                            }
                            catch (Exception e) 
                            {
                                e.printStackTrace();
                            }


                            // for (int i = 0; i < countMap.size(); i++) {
                            //     System.out.print("| ");
                            //     int currentBiggest = 0;
                            //     int keyValue = 0;
                            //     boolean hasChanged = false;
                            //     for (int j = 0; j < countMap.size(); j++)// 简单的找最大值打印
                            //     {
                            //         if (currentBiggest < countMap.get(j)) {
                            //             hasChanged = true;
                            //             currentBiggest = countMap.get(j);
                            //             keyValue = j;
                            //         }
                            //     }
                            //     if (hasChanged)
                            //     {
                            //         countMap.put(keyValue, -1);
                            //         System.out.println("Book " + keyValue + ": " + currentBiggest + " occurences");
                            //     }
                            //     else
                            //     {
                            //         //搜索countmap因为剩下的都是0或-1，我们从第一个键值对开始，检测下一个value是0的并输出
                            //     }
                            // }

                            System.out.println("-----------------");
                            countMap.clear();
                            waitingList.clear();// 清空缓冲
                            flag = false;
                            // 格式，按照arraylist大小，用index代表书本名字
                            // 按照大小顺序
                        }

                        // 消费者退出
                    };
                    scheduler.scheduleWithFixedDelay(analysisTask1, 5, 5, TimeUnit.SECONDS);
                    // scheduler.scheduleWithFixedDelay(() -> {
                    // if (taskFinished) {
                    // try {
                    // Thread.sleep(4000);
                    // } catch (InterruptedException e) {
                    // e.printStackTrace();
                    // }
                    // taskFinished = false; // 重新标记任务一为未结束
                    // printFinish = false;
                    // }
                    // }, 5, 0, TimeUnit.SECONDS);
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String line;
                List<String> bookLines = new ArrayList<>();
                Book b = new Book(bookLines, bookCounter);

                bookCounter++;
                boolean findTitle = false;
                while ((line = reader.readLine()) != null) {
                    if (findTitle == false) {
                        if (line.startsWith("Title:")) {
                            String bookTitle = line.substring(7).trim();
                            System.out.println("Received Book Title: " + bookTitle);
                            findTitle = true;
                        }
                    }
                    addToSharedList(line, b.connection);
                    b.bookLines.add(line);
                }
                writeBookToFile(b);
                clientSocket.close();
                // boolean result = clientMinus();
                // if (result == false)// 如果没有客户，退出进城池
                // {
                // scheduler.shutdown();
                // }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(args[1]);
        

        pattern = args[3].replaceAll("\"", "");
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server is running and listening on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                Thread clientThread = new Thread(new ClientHandler(clientSocket));
                clientThread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
