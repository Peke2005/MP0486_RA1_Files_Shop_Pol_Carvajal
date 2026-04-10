package regression;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.imageio.ImageIO;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.TypedQuery;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import dao.DaoImplObjectDB;
import model.Employee;
import view.LoginView;
import view.ShopView;

public class ShopRegressionTest {

	private static final Path EVIDENCE_DIR = Paths.get("evidence", "regression");
	private static final Duration UI_TIMEOUT = Duration.ofSeconds(10);

	public static void main(String[] args) throws Exception {
		System.setProperty("java.awt.headless", "false");
		Files.createDirectories(EVIDENCE_DIR);
		Path reportPath = EVIDENCE_DIR.resolve("regression-report.txt");
		Files.write(reportPath, new byte[0]);
		generateObjectDbUsersEvidence();

		int failures = 0;
		failures += runTest(reportPath, "1. Verificar login correcto accede al menu principal",
				() -> doTestSuccessfulLoginOpensMainMenu());
		failures += runTest(reportPath, "2. Verificar login incorrecto muestra mensaje de error",
				() -> doTestFailedLoginShowsError());

		disposeAllWindows();
		if (failures > 0) {
			System.out.println("FAIL - " + failures + " pruebas con error. Reporte: " + reportPath.toAbsolutePath());
			System.exit(1);
		}
		System.out.println("OK - 2 pruebas superadas. Reporte: " + reportPath.toAbsolutePath());
	}

	private static int runTest(Path reportPath, String name, CheckedRunnable test) throws Exception {
		List<String> reportLines = new ArrayList<String>();
		Instant start = Instant.now();
		try {
			disposeAllWindows();
			test.run();
			reportLines.add("PASS | " + name + " | " + Duration.between(start, Instant.now()).toMillis() + " ms");
			System.out.println("PASS | " + name);
			appendReport(reportPath, reportLines);
			return 0;
		} catch (Throwable error) {
			reportLines.add("FAIL | " + name + " | " + error.getClass().getSimpleName() + " | " + error.getMessage());
			System.out.println("FAIL | " + name + " -> " + error.getMessage());
			error.printStackTrace(System.out);
			appendReport(reportPath, reportLines);
			return 1;
		} finally {
			disposeAllWindows();
		}
	}

	private static void generateObjectDbUsersEvidence() throws Exception {
		DaoImplObjectDB dao = new DaoImplObjectDB();
		dao.connect();
		dao.disconnect();

		String objectDbPath = System.getProperty("shop.objectdb.path", "objects/users.odb");
		EntityManagerFactory factory = Persistence.createEntityManagerFactory(objectDbPath);
		EntityManager manager = factory.createEntityManager();
		TypedQuery<Employee> query = manager.createQuery("SELECT e FROM model.Employee e ORDER BY e.employeeId",
				Employee.class);
		List<Employee> employees = query.getResultList();
		manager.close();
		factory.close();

		writeObjectDbEvidenceImage(EVIDENCE_DIR.resolve("objectdb-users-evidence.png"), objectDbPath, employees);
	}

	private static void writeObjectDbEvidenceImage(Path outputPath, String objectDbPath, List<Employee> employees)
			throws Exception {
		int width = 1200;
		int lineHeight = 34;
		int lines = 6 + employees.size();
		int height = Math.max(360, lines * lineHeight + 40);

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, width, height);
		graphics.setColor(new Color(20, 20, 20));
		graphics.setFont(new Font("Consolas", Font.BOLD, 28));
		graphics.drawString("Evidencia ObjectDB - users", 30, 50);
		graphics.setFont(new Font("Consolas", Font.PLAIN, 21));
		graphics.drawString("Ruta base datos: " + objectDbPath, 30, 95);
		graphics.drawString("Consulta: SELECT e FROM model.Employee e ORDER BY e.employeeId", 30, 130);
		graphics.drawString("Registros encontrados: " + employees.size(), 30, 165);

		int y = 215;
		for (Employee employee : employees) {
			String row = "employeeId=" + employee.getEmployeeId() + " | name=" + employee.getName() + " | password="
					+ employee.getPassword();
			graphics.drawString(row, 30, y);
			y += lineHeight;
		}

		ImageIO.write(image, "png", outputPath.toFile());
		graphics.dispose();
	}

	private static void doTestSuccessfulLoginOpensMainMenu() throws Exception {
		LoginView loginView = onEdt(() -> {
			LoginView view = new LoginView();
			view.setVisible(true);
			return view;
		});

		try {
			setTextField(loginView, "textFieldEmployeeId", "123");
			setTextField(loginView, "textFieldPassword", "test");
			captureWindow(loginView, "login-ok-credentials.png");
			clickButton(loginView, "btnLogin");

			ShopView shopView = waitForWindow(() -> findVisibleWindow(ShopView.class));
			assertNotNull(shopView, "El login correcto debe abrir el menu principal");
			captureWindow(shopView, "login-ok-shopview.png");
			assertFalse(onEdt(() -> loginView.isDisplayable()), "La pantalla de login debe cerrarse tras acceder");
			disposeWindow(shopView);
		} finally {
			disposeWindow(loginView);
		}
	}

	private static void doTestFailedLoginShowsError() throws Exception {
		LoginView loginView = onEdt(() -> {
			LoginView view = new LoginView();
			view.setVisible(true);
			return view;
		});

		try {
			setTextField(loginView, "textFieldEmployeeId", "123");
			setTextField(loginView, "textFieldPassword", "bad-password");
			captureWindow(loginView, "login-error-credentials.png");
			DialogResult result = clickButtonAndCaptureDialog(loginView, "btnLogin", "login-error-dialog.png");

			assertEquals("Usuario o password incorrectos ", result.message,
					"El login incorrecto debe mostrar el mensaje de error esperado");
			assertEquals(JOptionPane.ERROR_MESSAGE, result.messageType, "El error de login debe mostrarse como error");
			assertNull(findVisibleWindow(ShopView.class), "No debe abrirse el menu principal si el login falla");
		} finally {
			disposeWindow(loginView);
		}
	}

	private static DialogResult clickButtonAndCaptureDialog(Object container, String buttonFieldName, String screenshotName)
			throws Exception {
		return triggerDialog(() -> clickButton(container, buttonFieldName), screenshotName);
	}

	private static void clickButton(Object container, String buttonFieldName) throws Exception {
		JButton button = getField(container, buttonFieldName, JButton.class);
		onEdt(() -> {
			button.doClick();
			return null;
		});
	}

	private static DialogResult triggerDialog(CheckedRunnable action, String screenshotName) throws Exception {
		FutureTask<Void> task = new FutureTask<Void>(() -> {
			try {
				action.run();
			} catch (Exception exception) {
				throw new RuntimeException(exception);
			}
			return null;
		});
		SwingUtilities.invokeLater(task);

		JDialog dialog = waitForWindow(() -> findVisibleOptionDialog());
		assertNotNull(dialog, "No se encontro el dialogo esperado");
		JOptionPane optionPane = findOptionPane(dialog);
		assertNotNull(optionPane, "No se encontro el JOptionPane esperado");

		captureWindow(dialog, screenshotName);
		String message = extractMessage(optionPane.getMessage());
		int messageType = optionPane.getMessageType();
		disposeWindow(dialog);
		task.get(UI_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		return new DialogResult(message, messageType);
	}

	private static <T extends Window> T waitForWindow(Supplier<T> supplier) throws Exception {
		long end = System.currentTimeMillis() + UI_TIMEOUT.toMillis();
		while (System.currentTimeMillis() < end) {
			T value = supplier.get();
			if (value != null) {
				return value;
			}
			Thread.sleep(100L);
		}
		return null;
	}

	private static JDialog findVisibleOptionDialog() {
		for (Window window : Window.getWindows()) {
			if (window instanceof JDialog && window.isShowing()) {
				JDialog dialog = (JDialog) window;
				if (findOptionPane(dialog) != null) {
					return dialog;
				}
			}
		}
		return null;
	}

	private static <T extends Window> T findVisibleWindow(Class<T> type) {
		for (Window window : Window.getWindows()) {
			if (type.isInstance(window) && window.isShowing()) {
				return type.cast(window);
			}
		}
		return null;
	}

	private static JOptionPane findOptionPane(Container container) {
		for (Component component : container.getComponents()) {
			if (component instanceof JOptionPane) {
				return (JOptionPane) component;
			}
			if (component instanceof Container) {
				JOptionPane optionPane = findOptionPane((Container) component);
				if (optionPane != null) {
					return optionPane;
				}
			}
		}
		return null;
	}

	private static String extractMessage(Object message) {
		if (message instanceof String) {
			return (String) message;
		}
		return String.valueOf(message);
	}

	private static void captureWindow(Window window, String screenshotName) throws Exception {
		if (GraphicsEnvironment.isHeadless()) {
			return;
		}
		Thread.sleep(700L);
		Point location = window.getLocationOnScreen();
		Rectangle bounds = new Rectangle(location.x, location.y, Math.max(window.getWidth(), 1), Math.max(window.getHeight(), 1));
		BufferedImage image = new Robot().createScreenCapture(bounds);
		ImageIO.write(image, "png", EVIDENCE_DIR.resolve(screenshotName).toFile());
	}

	private static void setTextField(Object target, String fieldName, String value) throws Exception {
		JTextField textField = getField(target, fieldName, JTextField.class);
		onEdt(() -> {
			textField.setText(value);
			return null;
		});
	}

	private static <T> T getField(Object target, String fieldName, Class<T> type) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		return type.cast(field.get(target));
	}

	private static void disposeWindow(Window window) {
		if (window == null) {
			return;
		}
		try {
			onEdt(() -> {
				window.dispose();
				return null;
			});
		} catch (Exception ignored) {
		}
	}

	private static void disposeAllWindows() {
		for (Window window : Window.getWindows()) {
			disposeWindow(window);
		}
		for (Frame frame : Frame.getFrames()) {
			disposeWindow(frame);
		}
	}

	private static void appendReport(Path reportPath, List<String> reportLines) throws Exception {
		if (reportLines.isEmpty()) {
			return;
		}
		Files.write(reportPath, reportLines, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
		Files.write(reportPath, List.of(""), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
	}

	private static <T> T onEdt(Callable<T> callable) throws Exception {
		if (SwingUtilities.isEventDispatchThread()) {
			return callable.call();
		}
		FutureTask<T> task = new FutureTask<T>(callable);
		SwingUtilities.invokeAndWait(task);
		return task.get();
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (!Objects.equals(expected, actual)) {
			throw new AssertionError(message + " | esperado=" + expected + " actual=" + actual);
		}
	}

	private static void assertNotNull(Object value, String message) {
		if (value == null) {
			throw new AssertionError(message);
		}
	}

	private static void assertNull(Object value, String message) {
		if (value != null) {
			throw new AssertionError(message + " | valor=" + value);
		}
	}

	private static void assertFalse(boolean value, String message) {
		if (value) {
			throw new AssertionError(message);
		}
	}

	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class DialogResult {
		private final String message;
		private final int messageType;

		private DialogResult(String message, int messageType) {
			this.message = message;
			this.messageType = messageType;
		}
	}
}
