package dao;

import java.util.ArrayList;
import java.util.List;

import model.Employee;
import model.Product;

public interface Dao {
	
	public void connect();

	public void disconnect();

	public Employee getEmployee(int employeeId, String password);
	
	public ArrayList<Product> getInventory();

	public boolean writeInventory(ArrayList<Product> inventory);
}