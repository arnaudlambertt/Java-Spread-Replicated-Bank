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
                connection.multicast(messages.toArray(new SpreadMessage[messages.size()]));
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

    public static void getQuickBalance(){
        System.out.println(String.valueOf(balance));
    }

    public static void getSyncedBalance(){
        synchronized (outstandingCollection){
            try {
                if(!outstandingCollection.isEmpty())
                    outstandingCollection.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println(String.valueOf(balance));
    }

    public static void getHistory(){
        for(Transaction t : executedList){
            System.out.println(t);
        }
    }

    public static void cleanHistory(){
        executedList.clear();
        System.out.println("Cleaned history.");
    }

    public static void memberInfo(){
        System.out.println(Arrays.toString(membersInfo));
    }

    public static void sleep(int duration){
        try {
            System.out.println("Sleeping for " + duration);
            Thread.sleep(duration * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
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
    public static void exit(){
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
    private static void createTransaction(String command) {
        try {
            Transaction t = new Transaction(command,id + "_" + String.valueOf(outstandingCounter++));
            outstandingCollection.add(t);
            System.out.println("Created transaction: " + t);
        } catch (NoSuchMethodException | IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
    }
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

    public static void main(String[] args) throws InterruptedException, NoSuchMethodException {

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
            connection.connect(InetAddress.getByName(serverAddress.equals("129.240.65.61") ? "127.0.0.1" : serverAddress), 4803, String.valueOf(id), false, true);

            group = new SpreadGroup();
            group.join(connection, accountName);

            daemonThread.start();

            Thread.sleep(500);
            synchronized (group){
                if(membersInfo.length < numberOfReplicas) {
                    System.out.println("Waiting for replicas...");
                    group.wait();
                }
            }

            CLI();

        } catch (SpreadException | IOException | JSchException e) {
            throw new RuntimeException(e);
        }
    }

    public static void deposit(double amount){
        balance += amount;
    }

    public static void withdraw(double amount){
        balance -= amount;
    }

    public static void addInterest(double amount){
        balance += balance * amount * 0.01;
    }

}