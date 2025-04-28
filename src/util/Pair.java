package util;
import java.util.Objects;

public class Pair<A, B> {
	private final A a;
	private final B b;
	
	public Pair(A a, B b) {
		this.a = a;
		this.b = b;
	}
	
	

	@Override
	public String toString() {
		return "Pair [a=" + a + ", b=" + b + "]";
	}



	@Override
	public int hashCode() {
		return Objects.hash(a, b);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Pair<?, ?> other = (Pair<?, ?>) obj;
		return Objects.equals(a, other.a) && Objects.equals(b, other.b);
	}

	public A getA() {
		return a;
	}

	public B getB() {
		return b;
	}
	
	
}
