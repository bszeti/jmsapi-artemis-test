package bszeti.artemis.jmsapitest;

import org.apache.qpid.jms.JmsConnectionFactory;

import javax.jms.*;

public class Application {

	public static void main(String[] args) {
		System.out.println("start");
		try {
			JmsConnectionFactory factory = new JmsConnectionFactory("amqp://localhost:61616");
			factory.setUsername("admin");
			factory.setPassword("admin");
			Connection connection = factory.createConnection();
			connection.start();
			System.out.println("connected");

			Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
			Queue targetQueue = session.createQueue("q1");
			MessageProducer producer = session.createProducer(targetQueue);

			TextMessage message = session.createTextMessage("message");
			// Header 600,000 chars long
			String header = String.format("%1$" + 600000 + "s", "").replace(" ", "H");
			message.setStringProperty("myheader", header);


			producer.send(message);
			session.commit();
			System.out.println("sent");
		} catch (Throwable e) {
			e.printStackTrace();
		}
		System.out.println("end");
	}
}
