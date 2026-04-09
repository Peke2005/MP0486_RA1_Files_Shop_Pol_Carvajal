package regression;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
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
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.bson.Document;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

import dao.DaoImplMongoDB;
import main.Shop;
import model.Product;
import utils.Constants;
import view.LoginView;
import view.ProductView;
import view.ShopView;

public class ShopRegressionTest {

	private static final String MONGO_URI = "mongodb://localhost:27017";
	private static final Path EVIDENCE_DIR = Paths.get("evidence", "regression");
	private static final Duration UI_TIMEOUT = Duration.ofSeconds(10);

	public static void main(String[] args) throws Exception {
		System.setProperty("java.awt.headless", "false");
		Files.createDirectories(EVIDENCE_DIR);
		Path reportPath = EVIDENCE_DIR.resolve("regression-report.txt");
		Files.write(reportPath, new byte[0]);

		int failures = 0;
		failures += runTest(reportPath, "1. Verificar carga inventario desde coleccion inventory",
				() -> doTestLoadInventoryFromCollection());
		failures += runTest(reportPath,
				"2a. Verificar exportar inventario escribe historical_inventory y muestra mensaje informacional",
				() -> doTestExportInventorySuccess());
		failures += runTest(reportPath,
				"2b. Verificar exportar inventario muestra mensaje error si falla historical_inventory",
				() -> doTestExportInventoryFailure());
		failures += runTest(reportPath, "3a. Verificar anadir producto inserta un nuevo documento",
				() -> doTestAddProductInsertsDocument());
		failures += runTest(reportPath,
				"3b. Verificar anadir stock actualiza el stock de un documento existente",
				() -> doTestAddStockUpdatesDocument());
		failures += runTest(reportPath, "3c. Verificar eliminar producto elimina un documento existente",
				() -> doTestRemoveProductDeletesDocument());
		failures += runTest(reportPath, "4. Verificar login correcto accede al menu principal",
				() -> doTestSuccessfulLoginOpensMainMenu());
		failures += runTest(reportPath, "5. Verificar login incorrecto muestra mensaje de error",
				() -> doTestFailedLoginShowsError());

		disposeAllWindows();
		if (failures > 0) {
			System.out.println("FAIL - " + failures + " pruebas con error. Reporte: " + reportPath.toAbsolutePath());
			System.exit(1);
		}
		System.out.println("OK - 8 pruebas superadas. Reporte: " + reportPath.toAbsolutePath());
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

	private static void doTestLoadInventoryFromCollection() throws Exception {
		String databaseName = createDatabaseName("inventory_load");
		seedDatabase(databaseName);
		System.setProperty("shop.mongodb.database", databaseName);

		Shop shop = new Shop(new DaoImplMongoDB());
		shop.loadInventory();

		assertEquals(4, shop.getInventory().size(), "El inventario cargado debe contener 4 productos");
		Product firstProduct = shop.getInventory().get(0);
		assertEquals(1, firstProduct.getId(), "El primer producto debe conservar su id");
		assertEquals("Manzana", firstProduct.getName(), "El primer producto debe venir de MongoDB");
		assertEquals(30, firstProduct.getStock(), "El stock del primer producto debe cargarse correctamente");
	}

	private static void doTestExportInventorySuccess() throws Exception {
		String databaseName = createDatabaseName("export_ok");
		seedDatabase(databaseName);
		System.setProperty("shop.mongodb.database", databaseName);

		ShopView view = onEdt(() -> {
			ShopView shopView = new ShopView();
			shopView.setVisible(true);
			return shopView;
		});

		try {
			DialogResult result = triggerDialog(() -> view.exportInventory(), "export-ok-dialog.png");
			assertEquals("Inventario exportado correctamente", result.message,
					"Debe mostrarse el mensaje informativo de exportacion correcta");
			assertEquals(JOptionPane.INFORMATION_MESSAGE, result.messageType,
					"El mensaje debe ser informativo");
			assertEquals(4L, countDocuments(databaseName, "historical_inventory"),
					"La exportacion debe insertar todos los productos en historical_inventory");
		} finally {
			disposeWindow(view);
		}
	}

	private static void doTestExportInventoryFailure() throws Exception {
		String databaseName = createDatabaseName("export_error");
		seedDatabase(databaseName);
		System.setProperty("shop.mongodb.database", databaseName);

		ShopView view = onEdt(() -> {
			ShopView shopView = new ShopView();
			shopView.setShop(new Shop(new DaoImplMongoDB()) {
				@Override
				public Boolean writeInventory() {
					return false;
				}
			});
			shopView.setVisible(true);
			return shopView;
		});

		try {
			DialogResult result = triggerDialog(() -> view.exportInventory(), "export-error-dialog.png");
			assertEquals("Error exportando el inventario", result.message,
					"Debe mostrarse el mensaje de error cuando la exportacion falla");
			assertEquals(JOptionPane.ERROR_MESSAGE, result.messageType, "El mensaje debe ser de error");
		} finally {
			disposeWindow(view);
		}
	}

	private static void doTestAddProductInsertsDocument() throws Exception {
		String databaseName = createDatabaseName("add_product");
		seedDatabase(databaseName);
		System.setProperty("shop.mongodb.database", databaseName);

		Shop shop = new Shop(new DaoImplMongoDB());
		shop.loadInventory();

		ProductView dialog = onEdt(() -> {
			ProductView productView = new ProductView(shop, Constants.OPTION_ADD_PRODUCT);
			productView.setVisible(true);
			return productView;
		});

		try {
			setTextField(dialog, "textFieldName", "Naranja");
			setTextField(dialog, "textFieldStock", "15");
			setTextField(dialog, "textFieldPrice", "2.5");

			DialogResult result = clickButtonAndCaptureDialog(dialog, "okButton", "add-product-dialog.png");
			assertEquals("Producto a\u00f1adido ", result.message, "Debe confirmarse el alta del producto");
			Document inserted = findOne(databaseName, "inventory", Filters.eq("name", "Naranja"));
			assertNotNull(inserted, "El producto nuevo debe insertarse en inventory");
			assertEquals(15, getNumber(inserted, "stock"), "El stock guardado debe coincidir");
		} finally {
			disposeWindow(dialog);
		}
	}

	private static void doTestAddStockUpdatesDocument() throws Exception {
		String databaseName = createDatabaseName("add_stock");
		seedDatabase(databaseName);
		System.setProperty("shop.mongodb.database", databaseName);

		Shop shop = new Shop(new DaoImplMongoDB());
		shop.loadInventory();

		ProductView dialog = onEdt(() -> {
			ProductView productView = new ProductView(shop, Constants.OPTION_ADD_STOCK);
			productView.setVisible(true);
			return productView;
		});

		try {
			setTextField(dialog, "textFieldName", "Manzana");
			setTextField(dialog, "textFieldStock", "5");

			DialogResult result = clickButtonAndCaptureDialog(dialog, "okButton", "add-stock-dialog.png");
			assertEquals("Stock actualizado ", result.message, "Debe confirmarse la actualizacion del stock");
			Document updated = findOne(databaseName, "inventory", Filters.eq("name", "Manzana"));
			assertNotNull(updated, "El producto actualizado debe seguir existiendo");
			assertEquals(35, getNumber(updated, "stock"), "El stock debe incrementarse en MongoDB");
		} finally {
			disposeWindow(dialog);
		}
	}

	private static void doTestRemoveProductDeletesDocument() throws Exception {
		String databaseName = createDatabaseName("remove_product");
		seedDatabase(databaseName);
		System.setProperty("shop.mongodb.database", databaseName);

		Shop shop = new Shop(new DaoImplMongoDB());
		shop.loadInventory();

		ProductView dialog = onEdt(() -> {
			ProductView productView = new ProductView(shop, Constants.OPTION_REMOVE_PRODUCT);
			productView.setVisible(true);
			return productView;
		});

		try {
			setTextField(dialog, "textFieldName", "Pera");

			DialogResult result = clickButtonAndCaptureDialog(dialog, "okButton", "remove-product-dialog.png");
			assertEquals("Producto eliminado", result.message, "Debe confirmarse la eliminacion del producto");
			Document deleted = findOne(databaseName, "inventory", Filters.eq("name", "Pera"));
			assertNull(deleted, "El producto debe eliminarse de inventory");
		} finally {
			disposeWindow(dialog);
		}
	}

	private static void doTestSuccessfulLoginOpensMainMenu() throws Exception {
		String databaseName = createDatabaseName("login_ok");
		seedDatabase(databaseName);
		System.setProperty("shop.mongodb.database", databaseName);

		LoginView loginView = onEdt(() -> {
			LoginView view = new LoginView();
			view.setVisible(true);
			return view;
		});

		try {
			setTextField(loginView, "textFieldEmployeeId", "123");
			setTextField(loginView, "textFieldPassword", "test");
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
		String databaseName = createDatabaseName("login_error");
		seedDatabase(databaseName);
		System.setProperty("shop.mongodb.database", databaseName);

		LoginView loginView = onEdt(() -> {
			LoginView view = new LoginView();
			view.setVisible(true);
			return view;
		});

		try {
			setTextField(loginView, "textFieldEmployeeId", "123");
			setTextField(loginView, "textFieldPassword", "bad-password");
			DialogResult result = clickButtonAndCaptureDialog(loginView, "btnLogin", "login-error-dialog.png");

			assertEquals("Usuario o password incorrectos ", result.message,
					"El login incorrecto debe mostrar el mensaje de error esperado");
			assertEquals(JOptionPane.ERROR_MESSAGE, result.messageType, "El error de login debe mostrarse como error");
			assertNull(findVisibleWindow(ShopView.class), "No debe abrirse el menu principal si el login falla");
		} finally {
			disposeWindow(loginView);
		}
	}

	private static void seedDatabase(String databaseName) {
		try (MongoClient client = new MongoClient(new MongoClientURI(MONGO_URI))) {
			MongoDatabase database = client.getDatabase(databaseName);
			MongoCollection<Document> inventory = database.getCollection("inventory");
			MongoCollection<Document> users = database.getCollection("users");

			inventory.insertOne(new Document("id", 1).append("name", "Manzana").append("price", 1.2)
					.append("available", true).append("stock", 30));
			inventory.insertOne(new Document("id", 2).append("name", "Pera").append("price", 1.5)
					.append("available", true).append("stock", 25));
			inventory.insertOne(new Document("id", 3).append("name", "Hamburguesa").append("price", 3.0)
					.append("available", true).append("stock", 20));
			inventory.insertOne(new Document("id", 4).append("name", "Fresa").append("price", 2.2)
					.append("available", true).append("stock", 40));

			users.insertOne(new Document("employeeId", 123).append("name", "Empleado Demo").append("password", "test"));
			users.insertOne(new Document("employeeId", 456).append("name", "Admin").append("password", "admin123"));
		}
	}

	private static long countDocuments(String databaseName, String collectionName) {
		try (MongoClient client = new MongoClient(new MongoClientURI(MONGO_URI))) {
			return client.getDatabase(databaseName).getCollection(collectionName).countDocuments();
		}
	}

	private static Document findOne(String databaseName, String collectionName, org.bson.conversions.Bson filter) {
		try (MongoClient client = new MongoClient(new MongoClientURI(MONGO_URI))) {
			return client.getDatabase(databaseName).getCollection(collectionName).find(filter).first();
		}
	}

	private static int getNumber(Document document, String key) {
		return ((Number) document.get(key)).intValue();
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
		Thread.sleep(250L);
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

	private static String createDatabaseName(String prefix) {
		return "shop_regression_" + prefix + "_" + System.currentTimeMillis();
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
