import { useNavigate, useParams } from "react-router-dom";
import { useContext, useEffect } from "react";
import { useState } from "react";
import AppContext from "../Context/Context";
import axios from "../axios";
import { toast } from "react-toastify";
import { getApiErrorMessage } from "../utils/apiError";

const Product = () => {
  const { id } = useParams();
  const { addToCart, removeFromCart, refreshData, isAdmin } = useContext(AppContext);
  const [product, setProduct] = useState(null);
  const [imageUrl, setImageUrl] = useState("");
  const [error, setError] = useState("");
  const navigate = useNavigate();

  useEffect(() => {
    const fetchProduct = async () => {
      try {
        const response = await axios.get(`/api/product/${id}`);
        setProduct(response.data);
        console.log(response.data);
        if (response.data.imageName) {
          fetchImage();
        }
      } catch (error) {
        console.error("Error fetching product:", error);
        setError(getApiErrorMessage(error, "Failed to load product"));
      }
    };

    const fetchImage = async () => {
      const response = await axios.get(`/api/product/${id}/image`, { responseType: "blob" });
      setImageUrl(URL.createObjectURL(response.data));
    };
    fetchProduct();
  }, [id]);

  const deleteProduct = async () => {
    try {
      await axios.delete(`/api/product/${id}`);
      removeFromCart(id);
      console.log("Product deleted successfully");
      toast.success("Product deleted successfully");
      refreshData();
      navigate("/");
    } catch (error) {
      console.error("Error deleting product:", error);
      toast.error(getApiErrorMessage(error, "Failed to delete product"));
    }
  };

  const handleEditClick = () => {
    navigate(`/product/update/${id}`);
  };

  const handlAddToCart = () => {
    addToCart(product);
    toast.success("Product added to cart");
  };

  if (!product) {
    if (error) {
      return (
        <div className="container mt-5 pt-5">
          <div className="alert alert-danger" role="alert">
            {error}
          </div>
        </div>
      );
    }

    return (
      <div className="container mt-5 pt-5">
        <div className="d-flex justify-content-center align-items-center" style={{ height: "400px" }}>
          <div className="spinner-border text-primary" role="status">
            <span className="visually-hidden">Loading...</span>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="container mt-5 pt-5">
      <div className="row">
        {/* Product Image */}
        <div className="col-md-6 mb-4">
          <div className="card border-0">
            <img
              src={imageUrl}
              alt={product.name}
              className="card-img-top img-fluid"
              style={{ maxHeight: "500px", objectFit: "contain" }}
            />
          </div>
        </div>

        {/* Product Details */}
        <div className="col-md-6">
          <div className="d-flex justify-content-between align-items-center mb-2">
            <span className="badge bg-secondary">{product.category}</span>
            <small className="text-muted">
              Listed: {product.releaseDate}
            </small>
          </div>

          <h2 className="text-capitalize mb-1">{product.name}</h2>
          <p className="text-muted fst-italic mb-4">~ {product.brand}</p>

          <div className="mb-4">
            <h5 className="mb-2">Product Description:</h5>
            <p>{product.description}</p>
          </div>

          <h3 className="fw-bold mb-3">₹ {product.price}</h3>

          <div className="d-grid gap-2 mb-3">
            <button
              className="btn btn-primary btn-lg"
              onClick={handlAddToCart}
              disabled={!product.productAvailable || product.stockQuantity == 0}
            >
              {product.stockQuantity !== 0 ? "Add to Cart" : "Out of Stock"}
            </button>
          </div>

          <p className="mb-4">
            <span className="me-2">Stock Available:</span>
            <span className="fw-bold text-success">{product.stockQuantity}</span>
          </p>

          {isAdmin && (
            <div className="d-flex gap-2">
              <button
                className="btn btn-outline-primary"
                type="button"
                onClick={handleEditClick}
              >
                <i className="bi bi-pencil me-1"></i>
                Update
              </button>

              <button
                className="btn btn-outline-danger"
                type="button"
                onClick={deleteProduct}
              >
                <i className="bi bi-trash me-1"></i>
                Delete
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Product;