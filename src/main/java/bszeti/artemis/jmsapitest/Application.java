package bszeti.artemis.jmsapitest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.jms.client.ActiveMQQueue;
import org.apache.activemq.artemis.jms.client.ActiveMQSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@EnableScheduling
@SpringBootApplication
public class Application implements CommandLineRunner {
	private static final Logger log = LoggerFactory.getLogger(Application.class);

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}



	@Value("${send.enabled}")
	Boolean sendEnabled;

	@Value("${send.queue}")
	String sendQueue;

	@Value("${send.threads}")
	int sendThreads;

	@Value("${send.count}")
	int sendCount;

	@Value("${send.message}")
	String sendMessage;

	@Value("${send.message.length}")
	Integer sendMessageLength;

	@Value("${send.headers.count}")
	Integer sendHeadersCount;

	@Value("${send.headers.length}")
	Integer sendHeadersLength;

	@Value("${send.useAnonymousProducers}")
	Boolean useAnonymousProducers;

	@Value("${receive.enabled}")
	Boolean receiveEnabled;

	@Value("${receive.queue}")
	String receiveQueue;

	@Value("${receive.selector}")
	String receiveSelector;

	@Value("${receive.filter}")
	String receiveFilter;

	@Autowired
	ConnectionFactory myConnectionFactory;

	@Autowired
	ConnectionFactoryConfig connectionFactoryConfig;

	@Autowired
	private ConfigurableApplicationContext applicationContext;

	Map<String,String> extraHeaders = new HashMap<>();
	private AtomicInteger sendCounter = new AtomicInteger();
	private int sendCounterLast = 0;
	private boolean stopReceive=false;

	@PostConstruct
	private void postConstruct(){
		if (sendMessageLength>0) {
			sendMessage = String.format("%1$"+sendMessageLength+ "s", "").replace(" ","M");
		}

		if (sendHeadersCount>0) {
			for(int i=0; i<sendHeadersCount; i++) {
				String key="extra"+i;
				String value=String.format("%1$"+sendHeadersLength+ "s", "").replace(" ","H");
				extraHeaders.put(key,value);
			}
		}
	}


	@Override
	public void run(String... strings) throws Exception {

		Connection connection = myConnectionFactory.createConnection();

		try {

			if (sendEnabled) {

				log.info("Sending messages");


				CountDownLatch latch = new CountDownLatch(sendThreads);
				Thread[] threads = new Thread[sendThreads];
				for (int i = 0; i < sendThreads; i++) {
					threads[i] = new Thread(
						new SendThread(connection, sendQueue, sendMessage, extraHeaders, sendCount, useAnonymousProducers, sendCounter, latch),
						"SendThread-"+i
					);
					threads[i].start();
				}

				//Wait
				while (!latch.await(1, TimeUnit.SECONDS)) {

				}

				//Stop
				for (Thread t : threads) {
					t.join();
				}
				log.info("Done sending");

				Thread.sleep(1000);
			}

			if (receiveEnabled) {
				log.info("Receiving message");

				Session session = connection.createSession(false,Session.AUTO_ACKNOWLEDGE);

				Queue targetQueue = null;
				switch (connectionFactoryConfig.getType()) {
					case "AMQP":
						targetQueue = (session).createQueue(receiveQueue);
						break;
					case "CORE":

						// Create queue remotely using CORE API
						ServerLocator locator = ActiveMQClient.createServerLocator(connectionFactoryConfig.getRemoteUrl());
						ClientSessionFactory factory = locator.createSessionFactory();

						ClientSession clientSession = factory.createSession(
							connectionFactoryConfig.getUsername(),connectionFactoryConfig.getPassword(),
							false, false, true, locator.isPreAcknowledge(),locator.getAckBatchSize()
						);
						try {
							clientSession.createQueue(new QueueConfiguration(receiveQueue).setFilterString(!receiveFilter.isEmpty() ? receiveFilter : null));
						} catch (ActiveMQQueueExistsException ex) {

							log.debug("Queue exists: {}", ex.getMessage());
						}

						// Connect to queue using JMS
						targetQueue = ((org.apache.activemq.artemis.jms.client.ActiveMQSession)session).createQueue(receiveQueue);
						log.info("targetq: {}",targetQueue);
						log.info("targetq: {}",((ActiveMQQueue)targetQueue).getQueueConfiguration());
//						log.info("FilterString: {}",((ActiveMQQueue)targetQueue).getQueueConfiguration().getFilterString());
						break;
					case "OPENWIRE":
						targetQueue = ((org.apache.activemq.ActiveMQSession)session).createQueue(receiveQueue);
						break;
				}

				MessageConsumer consumer =  receiveSelector.isEmpty() ? session.createConsumer( targetQueue ) : session.createConsumer( targetQueue, receiveSelector );

				connection.start();

				while (!stopReceive) {
					try {
						Message m = consumer.receive();
						if (m == null) throw new Exception("stopping");
						String mId = m.getJMSMessageID();
						String mBody = "non-text";
						if (m instanceof TextMessage) {
							mBody = ((TextMessage)m).getText();
						} else {
							mBody = m.toString();
						}

						log.info("Message: {} - {}", mId, mBody);
						log.debug("Message: {} - {}", mId, m.toString());
					} catch (Throwable e) {
						log.info("Consumer stopped", e);
						stopReceive = true;
					}
				}

			}

		} catch (Throwable e) {
			log.error("Stopping",e);
		} finally {
			connection.close();
			log.info("Stop applicationContext");
			applicationContext.close();
		}
	}


	@Scheduled(fixedRate = 1000)
	public void reportCurrentTime() {
		if (sendEnabled) {
			int current = sendCounter.get();
			int diff = current - sendCounterLast;
			sendCounterLast = current;
			log.info("send   : " + current + " - " + diff + "/s");
		}
	}
}
