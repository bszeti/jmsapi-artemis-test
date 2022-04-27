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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendThread implements Runnable{

    private static final Logger log = LoggerFactory.getLogger(SendThread.class);

    Connection connection;
    String queue;
    String message;
    int count;
    AtomicInteger sharedCounter;
    Map<String,String> extraHeaders;
    boolean useAnonymousProducers;
    CountDownLatch latch;
    long sendDelay;


    SendThread(Connection connection, String queue, String message, Map<String,String> extraHeaders, int count, long sendDelay, boolean useAnonymousProducers, AtomicInteger sharedCounter, CountDownLatch latch){
        this.connection=connection;
        this.queue=queue;
        this.message=message;
        this.extraHeaders=extraHeaders;
        this.count=count;
        this.sendDelay = sendDelay;
        this.sharedCounter = sharedCounter;
        this.useAnonymousProducers=useAnonymousProducers;
        this.latch=latch;

    }

    @Override
    public void run() {
        try {

            //Thread.sleep(15*1000);

            // Create Producer
//             Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            log.debug("Create session");
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue targetQueue = session.createQueue(queue);
            log.debug("Create producer");
            MessageProducer producer = session.createProducer( useAnonymousProducers ? null : targetQueue );

            connection.start();

            for (int i=0; i<count; i++) {
                // Create message
                // MessageProducer producerToUse = producer;
                // TextMessage outMessage = session.createTextMessage(message);

                //Wrong code
                log.debug("Per message - start");
                long start = System.currentTimeMillis();
                Session sessionRecreate = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
                log.debug("Per message - created session ({}ms)", System.currentTimeMillis()-start);
                Queue targetQueueRecreate = sessionRecreate.createQueue(queue);
                log.debug("Per message - created queue ({}ms)", System.currentTimeMillis()-start);
                MessageProducer producerToUse = sessionRecreate.createProducer( useAnonymousProducers ? null : targetQueueRecreate );
                log.debug("Per message - created producer ({}ms)", System.currentTimeMillis()-start);
                TextMessage outMessage = sessionRecreate.createTextMessage(message);
                log.debug("Per message - created message  ({}ms)", System.currentTimeMillis()-start);



                String uuid = UUID.randomUUID().toString();
                outMessage.setStringProperty("_AMQ_DUPL_ID", uuid);
                outMessage.setIntProperty("COUNTER", sharedCounter.incrementAndGet());
                for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                    outMessage.setStringProperty(entry.getKey(), entry.getValue());
                }

                //Send message
                if (producer.getDestination() == null) {
                    producerToUse.send(targetQueue, outMessage);
                } else {
                    producerToUse.send(outMessage);
                }
                log.debug("Per message - sent ({}ms)", System.currentTimeMillis()-start);

                //Delay
                Thread.sleep(sendDelay);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            latch.countDown();
        }

    }
}
