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

	@Autowired
	ConnectionFactory myConnectionFactory;


	@Autowired
	private ConfigurableApplicationContext applicationContext;

	Map<String,String> extraHeaders = new HashMap<>();
	private AtomicInteger sendCounter = new AtomicInteger();
	private int sendCounterLast = 0;

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

		try {

			if (sendEnabled) {

				log.info("Sending messages");

				Connection connection = myConnectionFactory.createConnection();

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
				connection.close();
				log.info("Done");

				Thread.sleep(1000);
			}

		} finally {
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
