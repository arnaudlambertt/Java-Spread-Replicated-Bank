import spread.AdvancedMessageListener;
import spread.SpreadException;
import spread.SpreadMessage;

import java.util.Arrays;

public class Listener implements AdvancedMessageListener {
    public void regularMessageReceived(SpreadMessage message) {
        Transaction transaction = null;
        String msg = null;
        try {
            msg = (String) message.getObject();
            if(msg.contains("updateBalance")){
                if(!AccountReplica.isInitialized) {
                    try{
                        AccountReplica.balance = Double.parseDouble(msg.split(" ")[1]);
                        AccountReplica.isInitialized = true;
                        synchronized (AccountReplica.connection){
                            AccountReplica.connection.notify();
                        }
                        System.out.println("Client successfully initialized. Balance = " + AccountReplica.balance);
                    }catch(NumberFormatException e){
                        System.err.println("Failed initializing client");
                    }
                }
                return;
            }
            try {
                transaction = new Transaction(msg);
            } catch (NoSuchMethodException | IllegalArgumentException e) {
                System.err.println(e.getMessage());
            }

            String method = transaction.command.split(" ")[0];
            double argument = Double.parseDouble(transaction.command.split(" ")[1]);

            if(!AccountReplica.executedList.contains(transaction)){

                AccountReplica.executedList.add(transaction);
                AccountReplica.orderCounter++;

                switch (method){
                    case "deposit":{
                        AccountReplica.deposit(argument);
                        break;
                    }
                    case "addInterest":{
                        AccountReplica.addInterest(argument);
                        break;
                    }
                    case "withdraw":{
                        AccountReplica.withdraw(argument);
                        break;
                    }
                    default:;
                }
                System.out.println("Executed transaction " + transaction);

            }else
                System.out.println(transaction + " already executed");

            synchronized (AccountReplica.outstandingCollection) {
                if (AccountReplica.outstandingCollection.remove(transaction) && AccountReplica.outstandingCollection.isEmpty())
                    AccountReplica.outstandingCollection.notify();
            }

        } catch (SpreadException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void membershipMessageReceived(SpreadMessage spreadMessage) {
        AccountReplica.membersInfo = spreadMessage.getMembershipInfo().getMembers();
        synchronized (AccountReplica.group){
            if(AccountReplica.membersInfo.length == AccountReplica.numberOfReplicas)
                AccountReplica.group.notify();
        }
        if(AccountReplica.membersInfo.length > AccountReplica.numberOfReplicas){ //only executes when new client joins
            SpreadMessage message = new SpreadMessage();
            message.addGroup(AccountReplica.group);
            message.setFifo();
            try {
                message.setObject("updateBalance " + AccountReplica.balance);
                AccountReplica.connection.multicast(message);
            } catch (SpreadException e) {
                throw new RuntimeException(e);
            }
        }
        if (AccountReplica.isInitialized){
            AccountReplica.numberOfReplicas = AccountReplica.membersInfo.length;
        }
    }
}