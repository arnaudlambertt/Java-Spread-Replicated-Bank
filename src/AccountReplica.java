import com.jcraft.jsch.*;
import org.ini4j.Ini;
import spread.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

public class AccountReplica {

    static UUID id;
    static String accountName;
    static String serverAddress;
    static int numberOfReplicas;
    static SpreadGroup group;
    static SpreadConnection connection;
    static double balance;
    static List<Transaction> executedList;
    static final List<Transaction> outstandingCollection = Collections.synchronizedList(new ArrayList<>());
    static int outstandingCounter;
    static Thread daemonThread;
    static int orderCounter;
    static SpreadGroup[] membersInfo;
    static File file;
    static Listener listener;
    static Session session;
    static boolean isInitialized = false;
    static boolean balanceUpdated = false;

    /**
     * Daemon responsible for sending outstanding transactions to the spread server every 10 seconds.
     */
    public static void outstandingCollectionDaemon(){

        while(!Thread.currentThread().isInterrupted()) {
            List<SpreadMessage> messages = new ArrayList<>();

            for (Transaction t : outstandingCollection){
                SpreadMessage message = new SpreadMessage();
                message.addGroup(group);
                message.setFifo();
                try {
                    message.setObject(t.toString());
                } catch (SpreadException e) {
                    throw new RuntimeException(e);
                }
                messages.add(message);
            }
            try {
                connection.multicast(messages.toArray(new SpreadMessage[0]));
            } catch (SpreadException e) {
                throw new RuntimeException(e);
            }

            try {
                synchronized (Thread.currentThread()){
                    Thread.currentThread().wait(10000);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Setup SSH Tunnel
     * @param host Host to connect to
     * @throws IOException when ini methods fail
     * @throws JSchException when tunnel fails
     */
    public static void setupTunnel (String host) throws IOException, JSchException {

        String username, password;
        Ini ini = null;

        File configFile = new File("conf.ini");
        try{
            ini = new Ini(configFile);
            username = ini.get("login","username");
            password = ini.get("login","password");
        }catch(IOException e){
            System.out.println("Enter uio username:");
            username = System.console().readLine();
            System.out.println("Enter password:");
            password = String.valueOf(System.console().readPassword());
        }

        int port = 22;
        int tunnelLocalPort = 4803;
        String tunnelRemoteHost = "127.0.0.255";
        int tunnelRemotePort = 4803;

        JSch jsch = new JSch();
        session = null;
        session = jsch.getSession(username, host, port);
        session.setPassword(password);
        LocalUserInfo lui = new LocalUserInfo();
        session.setUserInfo(lui);
        session.connect();
        session.setPortForwardingL(tunnelLocalPort,tunnelRemoteHost,tunnelRemotePort);

        if(ini == null){
            System.out.println("Save login (y/n)? :");
            String saveLogin = System.console().readLine();
            if (saveLogin.equals("y") || saveLogin.equals("yes")){
                if(configFile.createNewFile()) {
                    ini = new Ini(configFile);
                    ini.put("login", "username", username);
                    ini.put("login", "password", password);
                    ini.store();
                }
            }
        }
    }

    /**
     * Prints the balance instantaneously
     */
    public static void getQuickBalance(){
        System.out.println("Quick balance value: " + balance);
    }

    /**
     * Waits for outstandingCollection to be empty before printing the balance
     */
    public static void naiveGetSyncedBalance(){
        new Thread(() -> {
            synchronized (outstandingCollection){
                try {
                    if(!outstandingCollection.isEmpty())
                        outstandingCollection.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            System.out.println("Naive synced Balance value: " + balance);
        }).start();
    }

    /**
     * Waits for the last outstandingTransaction to be executed before printing the balance
     */
    public static void getSyncedBalance(){
        new Thread(() -> {

            Transaction outstandingTransaction = null;

            synchronized (outstandingCollection) {
                if (!outstandingCollection.isEmpty()) {
                    outstandingTransaction = outstandingCollection.get(outstandingCollection.size() - 1);
                }
            }

            if(outstandingTransaction != null){
                synchronized (outstandingTransaction) {
                    try {
                        outstandingTransaction.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            System.out.println("Synced Balance value: " + balance);
        }).start();
    }

    /**
     * Prints all transactions in the executedList
     */
    public static void getHistory(){
        System.out.println("Transaction history:");
        for(Transaction t : executedList){
            System.out.println("    " + t);
        }
        System.out.println("----------------------------------------------------------------");
    }

    /**
     * Clears the executedList
     */
    public static void cleanHistory(){
        executedList.clear();
        System.out.println("Cleaned history.");
    }

    /**
     * Prints info of group members
     */
    public static void memberInfo(){
        System.out.println(Arrays.toString(membersInfo));
    }

    /**
     * Makes client thread sleep for duration
     * @param duration Time to sleep for in seconds
     */
    public static void sleep(int duration){
        try {
            System.out.println("Sleeping for " + duration);
            Thread.sleep(duration * 1000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check transaction status (executed, outstanding, not found)
     * @param uniqueId Transaction ID
     */
    public static void checkTxStatus(String uniqueId){
        try{
            Transaction t = new Transaction("deposit 10", uniqueId);
            if(executedList.contains(t))
                System.out.println(executedList.get(executedList.indexOf(t)) + ": executed.");
            else if(outstandingCollection.contains(t))
                System.out.println(outstandingCollection.get(outstandingCollection.indexOf(t)) + ": outstanding transaction.");
            else
                System.out.println("Transaction " + uniqueId + " not found.");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Stops all running threads and disconnects
     */
    public static void exit(){

        outstandingCollection.forEach(transaction -> { synchronized (transaction){
            transaction.notifyAll();
        }});

        synchronized (daemonThread){
            daemonThread.notify();
            daemonThread.interrupt();
        }
        try {
            connection.remove(listener);
            connection.disconnect();
            if(session != null)
                session.disconnect();
        } catch (SpreadException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates new transaction with uniqueID
     * @param command Method + arguments
     */
    private static void createTransaction(String command) {
        try {
            Transaction t = new Transaction(command,id + "_" + (outstandingCounter++));
            outstandingCollection.add(t);
            System.out.println("Created transaction: " + t);
        } catch (NoSuchMethodException | IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Command-line Interface
     * Takes input from user or file
     */
    public static void CLI(){
        String command;
        String method;
        String args = "";

        Scanner input;
        if(file != null && file.exists()) {
            try {
                input = new Scanner(file, "UTF-8");
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        else{
            System.out.println(
                "Commands: \n" +
                "addInterest <amount>\n" +
                "withdraw <amount>\n" +
                "deposit <amount>\n" +
                "getQuickBalance\n" +
                "getSyncedBalance\n" +
                "getHistory\n" +
                "checkTxStatus\n" +
                "cleanHistory\n" +
                "memberInfo\n" +
                "sleep <duration>\n"
            );
            input = new Scanner(System.in);
        }
        do{
            command = input.nextLine();
            method = command.split(" ")[0];
            if(command.split(" ").length > 1)
                args = command.split(" ")[1];

            switch (method){
                case "addInterest":
                case "withdraw":
                case "deposit":{
                    createTransaction(command);
                    break;
                }
                case "getQuickBalance":{
                    getQuickBalance();
                    break;
                }
                case "getSyncedBalance":{
                    getSyncedBalance();
                    break;
                }
                case "getHistory":{
                    getHistory();
                    break;
                }
                case "cleanHistory":{
                    cleanHistory();
                    break;
                }
                case "memberInfo":{
                    memberInfo();
                    break;
                }
                case "checkTxStatus":{
                    if(args.contains("<"))
                        args = id + "_" + (outstandingCounter-1);
                    checkTxStatus(args);
                    break;
                }
                case "sleep": {
                    if (args.isEmpty())
                        System.out.println(method + " Error: bad argument");
                    try {
                        sleep(Integer.parseInt(args));
                    } catch (NumberFormatException e) {
                        System.out.println(method + " Error: bad argument");
                    }
                    break;
                }
                case "exit":{
                    exit();
                    break;
                }
                default:{
                    System.out.println("Error: No such method");
                }
            }
        }while(!method.equals("exit"));
    }

    /**
     * verify arguments, initialize attributes, setups the tunnel if needed,
     * connects to spread, starts the multicasting daemon and waits for replicas to be available
     * @param args java arguments
     */
    public static void main(String[] args) {

        if(args.length < 3)
            throw new IllegalArgumentException();

        serverAddress = args[0];
        accountName = args[1];
        numberOfReplicas = Integer.parseInt(args[2]);
        if(args.length > 3)
            file = new File(args[3]);

        id = UUID.randomUUID();
        balance = 0.0;
        executedList = new ArrayList<>();
        connection = new SpreadConnection();
        daemonThread = new Thread(AccountReplica::outstandingCollectionDaemon);
        listener = new Listener();
        membersInfo = new SpreadGroup[0];

        try {
            if(serverAddress.equals("129.240.65.61"))
                setupTunnel(serverAddress);

            connection.add(listener);
            connection.connect(InetAddress.getByName(serverAddress.equals("129.240.65.61") ? "127.0.0.1" : serverAddress), 4803, id.toString(), false, true);

            group = new SpreadGroup();

            daemonThread.start();

            synchronized (group){
                group.join(connection, accountName);
                group.wait();
                isInitialized = true;
            }

            Thread.sleep(500);

            CLI();

        } catch (SpreadException | IOException | JSchException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds amount to balance
     * @param amount to deposit
     */
    public static void deposit(double amount){
        balance += amount;
    }

    /**
     * Substracts amount from balance
     * @param amount to withdraw
     */
    public static void withdraw(double amount){
        balance -= amount;
    }

    /**
     * Adds interest to balance
     * @param amount Interest to add in percentage
     */
    public static void addInterest(double amount){
        balance += balance * amount * 0.01;
    }

}