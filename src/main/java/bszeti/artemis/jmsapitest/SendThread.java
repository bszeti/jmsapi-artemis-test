package bszeti.artemis.jmsapitest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

public class SendThread implements Runnable{

    Connection connection;
    String queue;
    String message;
    int count;
    AtomicInteger sharedCounter;
    Map<String,String> extraHeaders;
    boolean useAnonymousProducers;
    CountDownLatch latch;


    SendThread(Connection connection, String queue, String message, Map<String,String> extraHeaders, int count, boolean useAnonymousProducers, AtomicInteger sharedCounter, CountDownLatch latch){
        this.connection=connection;
        this.queue=queue;
        this.message=message;
        this.extraHeaders=extraHeaders;
        this.count=count;
        this.sharedCounter = sharedCounter;
        this.useAnonymousProducers=useAnonymousProducers;
        this.latch=latch;

    }

    @Override
    public void run() {
        try {

            // Create Producer
            // Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            Session session = connection.createSession();
            Queue targetQueue = session.createQueue(queue);
            MessageProducer producer = session.createProducer( useAnonymousProducers ? null : targetQueue );

            connection.start();

            for (int i=0; i<count; i++) {
                // Create message
                TextMessage outMessage = session.createTextMessage(message);
                String uuid = UUID.randomUUID().toString();
                outMessage.setStringProperty("_AMQ_DUPL_ID", uuid);
                outMessage.setIntProperty("COUNTER", sharedCounter.incrementAndGet());
                for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                    outMessage.setStringProperty(entry.getKey(), entry.getValue());
                }

                //Send message
                if (producer.getDestination() == null) {
                    producer.send(targetQueue, outMessage);
                } else {
                    producer.send(outMessage);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            latch.countDown();
        }

    }
}
