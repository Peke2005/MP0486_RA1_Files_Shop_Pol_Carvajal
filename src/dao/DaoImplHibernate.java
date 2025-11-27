package dao;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;

import model.Employee;
import model.Product;
import model.ProductHistory;

public class DaoImplHibernate implements Dao {
	private static final SessionFactory sessionFactory = buildSessionFactory();

	private static SessionFactory buildSessionFactory() {
		try {
			return new Configuration().configure().buildSessionFactory();
		} catch (Throwable ex) {
			ex.printStackTrace();
			throw new ExceptionInInitializerError(ex);
		}
	}

	@Override
	public void connect() {
	}

	@Override
	public void disconnect() {
	}

	@Override
	public Employee getEmployee(int employeeId, String password) {
		return null;
	}

	@Override
	public ArrayList<Product> getInventory() {
		Transaction transaction = null;
		List<Product> products = new ArrayList<Product>();
		try (Session session = sessionFactory.openSession()) {
			transaction = session.beginTransaction();
			products = session.createQuery("from Product", Product.class).list();
			transaction.commit();
		} catch (Exception ex) {
			if (transaction != null) {
				transaction.rollback();
			}
			ex.printStackTrace();
		}
		return new ArrayList<Product>(products);
	}

	@Override
	public boolean writeInventory(ArrayList<Product> products) {
		if (products == null) {
			return false;
		}
		Transaction transaction = null;
		try (Session session = sessionFactory.openSession()) {
			transaction = session.beginTransaction();
			Timestamp createdAt = new Timestamp(System.currentTimeMillis());
			for (Product product : products) {
				ProductHistory history = new ProductHistory();
				history.setAvailable(product.isAvailable());
				history.setCreatedAt(createdAt);
				history.setIdProduct(product.getId());
				history.setName(product.getName());
				history.setPrice(product.getPrice());
				history.setStock(product.getStock());
				session.save(history);
			}
			transaction.commit();
			return true;
		} catch (Exception ex) {
			if (transaction != null) {
				transaction.rollback();
			}
			ex.printStackTrace();
		}
		return false;
	}

	@Override
	public void addProduct(Product product) {
		Transaction transaction = null;
		try (Session session = sessionFactory.openSession()) {
			transaction = session.beginTransaction();
			session.save(product);
			transaction.commit();
		} catch (Exception ex) {
			if (transaction != null) {
				transaction.rollback();
			}
			ex.printStackTrace();
		}
	}

	@Override
	public void updateProduct(Product product) {
		Transaction transaction = null;
		try (Session session = sessionFactory.openSession()) {
			transaction = session.beginTransaction();
			session.update(product);
			transaction.commit();
		} catch (Exception ex) {
			if (transaction != null) {
				transaction.rollback();
			}
			ex.printStackTrace();
		}
	}

	@Override
	public void deleteProduct(int productId) {
		Transaction transaction = null;
		try (Session session = sessionFactory.openSession()) {
			transaction = session.beginTransaction();
			Product product = session.get(Product.class, productId);
			if (product != null) {
				session.delete(product);
			}
			transaction.commit();
		} catch (Exception ex) {
			if (transaction != null) {
				transaction.rollback();
			}
			ex.printStackTrace();
		}
	}
}
