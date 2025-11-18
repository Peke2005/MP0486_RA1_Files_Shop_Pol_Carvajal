package dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import model.Amount;
import model.Employee;
import model.Product;

public class DaoImplJDBC implements Dao {
	Connection connection;

	@Override
	public void connect() {
		// Define connection parameters
		String url = "jdbc:mysql://localhost:3306/shop";
		String user = "root";
		String pass = "";
		try {
			this.connection = DriverManager.getConnection(url, user, pass);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public void disconnect() {
		// TODO Auto-generated method stub
		if (connection != null) {
			try {
				connection.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	@Override
	public Employee getEmployee(int employeeId, String password) {
		Employee employee = null;
		String query = "select * from employee where employeeId= ? and password = ? ";

		try (PreparedStatement ps = connection.prepareStatement(query)) {
			ps.setInt(1, employeeId);
			ps.setString(2, password);
			// System.out.println(ps.toString());
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					employee = new Employee(rs.getInt(1), rs.getString(2), rs.getString(3));
				}
			}
		} catch (SQLException e) {
			// in case error in SQL
			e.printStackTrace();
		}
		return employee;
	}

	@Override
	public ArrayList<Product> getInventory() {
		ArrayList<Product> inventory = new ArrayList<Product>();
		String query = "select * from inventory";
		try {
			connect();
			Statement stmt = connection.createStatement();
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				Amount amount = new Amount(rs.getDouble("wholesalerPrice"));
				Product product = new Product(rs.getInt("id"), rs.getString("name"), amount, rs.getBoolean("available"),
						rs.getInt("stock"));
				inventory.add(product);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			disconnect();
		}
		return inventory;
	}

	@Override
	public boolean writeInventory(ArrayList<Product> products) {
		String query = "Insert into historical_inventory (id_product, name, wholesalerPrice, available, stock, created_at) values (?, ?, ?, ?, ?, ?)";
		try {
			connect();
			PreparedStatement ps = connection.prepareStatement(query);
			for (Product product : products) {
				ps.setInt(1, product.getId());
				ps.setString(2, product.getName());
				ps.setDouble(3, product.getWholesalerPrice().getValue());
				ps.setBoolean(4, product.isAvailable());
				ps.setInt(5, product.getStock());
				ps.setTimestamp(6, new java.sql.Timestamp(System.currentTimeMillis()));
				ps.executeUpdate();
			}
			return true;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			disconnect();
		}
		return false;
	}

	@Override
	public void addProduct(Product product) {
		String query = "insert into inventory (name, wholesalerPrice, available, stock) values (?, ?, ?, ?)";
		try {
			connect();
			PreparedStatement ps = connection.prepareStatement(query);
			ps.setString(1, product.getName());
			ps.setDouble(2, product.getWholesalerPrice().getValue());
			ps.setBoolean(3, true);
			ps.setInt(4, product.getStock());
			ps.executeUpdate();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			disconnect();
		}

	}

	@Override
	public void updateProduct(Product product) {
		String query = "Update inventory set stock = ? where id = ?";
		try {
			connect();
			PreparedStatement ps = connection.prepareStatement(query);
			ps.setInt(1, product.getStock());
			ps.setInt(2, product.getId());
			ps.executeUpdate();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			disconnect();
		}

	}

	@Override
	public void deleteProduct(int productId) {
		String query = "Delete from inventory where id = ?";
		try {
			connect();
			PreparedStatement ps = connection.prepareStatement(query);
			ps.setInt(1, productId);
			ps.executeUpdate();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			disconnect();
		}
	}

}