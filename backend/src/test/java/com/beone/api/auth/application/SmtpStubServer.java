package com.beone.api.auth.application;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * 받은 메일을 기록만 하는 루프백({@code 127.0.0.1}) SMTP 서버. 외부로 보내지 않는다. 시험에 필요한 명령(EHLO·AUTH PLAIN·MAIL·RCPT·DATA·
 * RSET·NOOP·QUIT)만 다루고 STARTTLS는 지원하지 않으므로, 시험 설정에서만 STARTTLS 필수를 끈다.
 */
public final class SmtpStubServer implements AutoCloseable {

	private final ServerSocket server;

	private final ExecutorService executor = Executors.newCachedThreadPool();

	private final List<Received> received = new CopyOnWriteArrayList<>();

	public SmtpStubServer() {
		try {
			this.server = new ServerSocket();
			this.server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		this.executor.submit(this::accept);
	}

	public int port() {
		return this.server.getLocalPort();
	}

	public List<Received> received() {
		return List.copyOf(this.received);
	}

	public void clear() {
		this.received.clear();
	}

	@Override
	public void close() {
		try {
			this.server.close();
		}
		catch (IOException ignored) {
			// 닫는 중 오류는 시험 결과와 무관하다
		}
		this.executor.shutdownNow();
	}

	private void accept() {
		while (!this.server.isClosed()) {
			try {
				Socket socket = this.server.accept();
				this.executor.submit(() -> handle(socket));
			}
			catch (IOException ex) {
				return;
			}
		}
	}

	private void handle(Socket socket) {
		try (socket;
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
				OutputStream out = socket.getOutputStream()) {
			write(out, "220 stub ESMTP");
			String from = null;
			List<String> recipients = new ArrayList<>();
			String line;
			while ((line = in.readLine()) != null) {
				String upper = line.toUpperCase();
				if (upper.startsWith("EHLO")) {
					write(out, "250-stub\r\n250-AUTH PLAIN\r\n250 OK");
				}
				else if (upper.startsWith("HELO") || upper.startsWith("RSET") || upper.startsWith("NOOP")) {
					write(out, "250 OK");
				}
				else if (upper.startsWith("AUTH PLAIN")) {
					if (line.trim().length() == "AUTH PLAIN".length()) {
						write(out, "334 ");
						in.readLine();
					}
					write(out, "235 2.7.0 OK");
				}
				else if (upper.startsWith("MAIL FROM:")) {
					from = address(line);
					recipients = new ArrayList<>();
					write(out, "250 OK");
				}
				else if (upper.startsWith("RCPT TO:")) {
					recipients.add(address(line));
					write(out, "250 OK");
				}
				else if (upper.equals("DATA")) {
					write(out, "354 End data with <CR><LF>.<CR><LF>");
					StringBuilder data = new StringBuilder();
					String dataLine;
					while ((dataLine = in.readLine()) != null && !dataLine.equals(".")) {
						data.append(dataLine.startsWith("..") ? dataLine.substring(1) : dataLine).append("\r\n");
					}
					this.received.add(new Received(from, List.copyOf(recipients), data.toString()));
					write(out, "250 OK");
				}
				else if (upper.startsWith("QUIT")) {
					write(out, "221 Bye");
					return;
				}
				else {
					write(out, "502 Not implemented");
				}
			}
		}
		catch (IOException ex) {
			// 클라이언트가 끊은 경우다
		}
	}

	private static String address(String line) {
		int start = line.indexOf('<');
		int end = line.indexOf('>');
		return (start >= 0 && end > start) ? line.substring(start + 1, end) : line.substring(line.indexOf(':') + 1).trim();
	}

	private static void write(OutputStream out, String reply) throws IOException {
		out.write((reply + "\r\n").getBytes(StandardCharsets.UTF_8));
		out.flush();
	}

	/**
	 * 받은 메일 한 통.
	 * @param from 봉투 발신 주소
	 * @param recipients 봉투 수신 주소
	 * @param raw DATA 원문
	 */
	public record Received(String from, List<String> recipients, String raw) {

		/** 헤더·본문을 해석한 메시지. */
		public MimeMessage message() {
			try {
				return new MimeMessage(Session.getInstance(new Properties()),
						new ByteArrayInputStream(this.raw.getBytes(StandardCharsets.UTF_8)));
			}
			catch (MessagingException ex) {
				throw new IllegalStateException(ex);
			}
		}

		/** 해석한 본문 문자열. */
		public String text() {
			try {
				return message().getContent().toString();
			}
			catch (MessagingException | IOException ex) {
				throw new IllegalStateException(ex);
			}
		}

	}

}
