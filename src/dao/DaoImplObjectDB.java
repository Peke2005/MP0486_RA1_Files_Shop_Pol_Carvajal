package dao;

import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.TypedQuery;

import model.Employee;
import model.Product;

public class DaoImplObjectDB implements Dao {

	private static final String DEFAULT_OBJECTDB_PATH = "objects/users.odb";
	private EntityManagerFactory entityManagerFactory;
	private EntityManager entityManager;

	@Override
	public void connect() {
		if (entityManagerFactory == null || !entityManagerFactory.isOpen()) {
			String databasePath = getDatabasePath();
			ensureParentDirectory(databasePath);
			entityManagerFactory = Persistence.createEntityManagerFactory(databasePath);
		}
		if (entityManager == null || !entityManager.isOpen()) {
			entityManager = entityManagerFactory.createEntityManager();
			seedDefaultUsersIfNeeded();
		}
	}

	@Override
	public void disconnect() {
		if (entityManager != null && entityManager.isOpen()) {
			entityManager.close();
			entityManager = null;
		}
		if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
			entityManagerFactory.close();
			entityManagerFactory = null;
		}
	}

	@Override
	public Employee getEmployee(int employeeId, String password) {
		ensureConnected();
		TypedQuery<Employee> query = entityManager.createQuery(
				"SELECT e FROM model.Employee e WHERE e.employeeId = :employeeId AND e.password = :password",
				Employee.class);
		query.setParameter("employeeId", employeeId);
		query.setParameter("password", password);

		ArrayList<Employee> results = new ArrayList<Employee>(query.getResultList());
		if (results.isEmpty()) {
			return null;
		}
		return results.get(0);
	}

	@Override
	public ArrayList<Product> getInventory() {
		throw new UnsupportedOperationException("ObjectDB DAO solo soporta login de empleados");
	}

	@Override
	public boolean writeInventory(ArrayList<Product> inventory) {
		throw new UnsupportedOperationException("ObjectDB DAO solo soporta login de empleados");
	}

	@Override
	public void addProduct(Product product) {
		throw new UnsupportedOperationException("ObjectDB DAO solo soporta login de empleados");
	}

	@Override
	public void updateProduct(Product product) {
		throw new UnsupportedOperationException("ObjectDB DAO solo soporta login de empleados");
	}

	@Override
	public void deleteProduct(int productId) {
		throw new UnsupportedOperationException("ObjectDB DAO solo soporta login de empleados");
	}

	private void ensureConnected() {
		if (entityManager == null || !entityManager.isOpen()) {
			connect();
		}
	}

	private String getDatabasePath() {
		return System.getProperty("shop.objectdb.path", DEFAULT_OBJECTDB_PATH);
	}

	private void seedDefaultUsersIfNeeded() {
		TypedQuery<Long> countQuery = entityManager.createQuery("SELECT COUNT(e) FROM model.Employee e", Long.class);
		long totalEmployees = countQuery.getSingleResult().longValue();
		if (totalEmployees > 0) {
			return;
		}

		entityManager.getTransaction().begin();
		entityManager.persist(new Employee(123, "Empleado Demo", "test"));
		entityManager.persist(new Employee(456, "Admin", "admin123"));
		entityManager.getTransaction().commit();
	}

	private void ensureParentDirectory(String databasePath) {
		Path parent = Paths.get(databasePath).toAbsolutePath().getParent();
		if (parent == null) {
			return;
		}
		try {
			Files.createDirectories(parent);
		} catch (Exception exception) {
			throw new RuntimeException("No se pudo crear carpeta para ObjectDB: " + parent, exception);
		}
	}
}
