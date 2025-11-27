package model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

@Entity
@Table(name = "inventory")
public class Product {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;
	@Column(name = "name")
	private String name;
	@Column(name = "price")
	private double price;
	@Transient
	private Amount publicPrice;
	@Transient
	private Amount wholesalerPrice;
	@Column(name = "available")
	private boolean available;
	@Column(name = "stock")
	private int stock;
	@Transient
	private static int totalProducts;

	public final static double EXPIRATION_RATE = 0.60;

	public Product() {
		super();
	}

	public Product(String name, Amount wholesalerPrice, boolean available, int stock) {
		super();
		this.id = 0;
		this.name = name;
		this.price = wholesalerPrice.getValue();
		refreshPricesFromBase();
		this.available = available;
		this.stock = stock;
		totalProducts++;
	}

	public Product(int id, String name, Amount wholesalerPrice, boolean available, int stock) {
		super();
		this.id = id;
		this.name = name;
		this.price = wholesalerPrice.getValue();
		refreshPricesFromBase();
		this.available = available;
		this.stock = stock;
		totalProducts++;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Amount getPublicPrice() {
		ensurePublicPrice();
		return publicPrice;
	}

	public void setPublicPrice(Amount publicPrice) {
		this.publicPrice = publicPrice;
	}

	public Amount getWholesalerPrice() {
		ensureWholesalerPrice();
		return wholesalerPrice;
	}

	public void setWholesalerPrice(Amount wholesalerPrice) {
		this.wholesalerPrice = wholesalerPrice;
		if (wholesalerPrice != null) {
			this.price = wholesalerPrice.getValue();
			this.publicPrice = new Amount(this.price * 2);
		}
	}

	public boolean isAvailable() {
		return available;
	}

	public void setAvailable(boolean available) {
		this.available = available;
	}

	public int getStock() {
		return stock;
	}

	public void setStock(int stock) {
		this.stock = stock;
	}

	public double getPrice() {
		return price;
	}

	public void setPrice(double price) {
		this.price = price;
		refreshPricesFromBase();
	}

	public static int getTotalProducts() {
		return totalProducts;
	}

	public static void setTotalProducts(int totalProducts) {
		Product.totalProducts = totalProducts;
	}

	public void expire() {
		this.getPublicPrice().setValue(this.getPublicPrice().getValue() * EXPIRATION_RATE);
	}

	@Override
	public String toString() {
		return "Product [name=" + name + ", publicPrice=" + getPublicPrice() + ", wholesalerPrice="
				+ getWholesalerPrice() + ", available=" + available + ", stock=" + stock + "]";
	}

	private void ensurePublicPrice() {
		if (publicPrice == null) {
			publicPrice = new Amount(price * 2);
		}
	}

	private void ensureWholesalerPrice() {
		if (wholesalerPrice == null) {
			wholesalerPrice = new Amount(price);
		}
	}

	private void refreshPricesFromBase() {
		wholesalerPrice = new Amount(price);
		publicPrice = new Amount(price * 2);
	}

}
