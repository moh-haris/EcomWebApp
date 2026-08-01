import { useState, useEffect } from "react";
import { useNavigate, useParams } from "react-router-dom";
import axios from "../axios";
import { toast } from "react-toastify";
import { getApiErrorMessage } from "../utils/apiError";

const normalizeReleaseDateForInput = (releaseDate) => {
  if (!releaseDate) {
    return "";
  }

  if (typeof releaseDate === "string") {
    if (/^\d{2}-\d{2}-\d{4}$/.test(releaseDate)) {
      const [day, month, year] = releaseDate.split("-");
      return `${year}-${month}-${day}`;
    }

    if (/^\d{4}-\d{2}-\d{2}$/.test(releaseDate)) {
      return releaseDate;
    }

    return releaseDate.slice(0, 10);
  }

  if (releaseDate instanceof Date) {
    return releaseDate.toISOString().slice(0, 10);
  }

  return "";
};

const UpdateProduct = () => {
  const { id } = useParams();
  const [product, setProduct] = useState({});
  const [image, setImage] = useState();
  const [updateProduct, setUpdateProduct] = useState({
    id: null,
    name: "",
    description: "",
    brand: "",
    price: "",
    category: "",
    releaseDate: "",
    productAvailable: false,
    stockQuantity: "",
  });

  const [imageChanged, setImageChanged] = useState(false);
  const [validated, setValidated] = useState(false);
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState("");
  

  useEffect(() => {
    const fetchProduct = async () => {
      try {
        const response = await axios.get(`/api/product/${id}`);
        const responseProduct = {
          ...response.data,
          releaseDate: normalizeReleaseDateForInput(response.data.releaseDate),
        };

        setProduct(responseProduct);

        console.log(responseProduct,'update product response')
      
        const responseImage = await axios.get(`/api/product/${id}/image`, { responseType: "blob" });
       const imageFile = await converUrlToFile(responseImage.data,responseProduct.imageName)
        setImage(imageFile);     
        setUpdateProduct(responseProduct);
      } catch (error) {
        console.error("Error fetching product:", error);
        setLoadError(getApiErrorMessage(error, "Failed to load product"));
      }
    };

    fetchProduct();
  }, [id]);

  useEffect(() => {
    console.log("image Updated", image);
  }, [image]);


  const navigate = useNavigate();

  const validateForm = () => {
    const newErrors = {};

    if (!updateProduct.name?.trim()) newErrors.name = "Name is required";
    if (!updateProduct.brand?.trim()) newErrors.brand = "Brand is required";
    if (!updateProduct.description?.trim()) newErrors.description = "Description is required";
    if (!updateProduct.price || Number(updateProduct.price) <= 0) newErrors.price = "Price must be greater than zero";
    if (!updateProduct.category) newErrors.category = "Category is required";
    if (updateProduct.stockQuantity === "" || Number(updateProduct.stockQuantity) < 0) newErrors.stockQuantity = "Stock quantity cannot be negative";
    if (!updateProduct.releaseDate) newErrors.releaseDate = "Release date is required";

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const converUrlToFile = async(blobData, fileName) => {
    const file = new File([blobData], fileName, { type: blobData.type });
    return file;
  }
 
  const handleSubmit = async(e) => {
    setValidated(true);
    e.preventDefault();
    if (!validateForm()) {
      return;
    }
    setLoading(true);
    console.log("images", image)
    console.log("productsdfsfsf", updateProduct)
    const updatedProduct = new FormData();
    if (imageChanged && image) {
      updatedProduct.append("imageFile", image);
    } else {
      // Send null or empty value when no image is selected by user
      updatedProduct.append("imageFile", null);
    }
    
    updatedProduct.append(
      "product",
      new Blob([JSON.stringify(updateProduct)], { type: "application/json" })
    );
  

  console.log("formData : ", updatedProduct)
    axios
      .put(`/api/product/${id}`, updatedProduct, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      })
      .then(() => {
        console.log("Product updated successfully:", updatedProduct);
        toast.success("product updated successfully")
      })
      .catch((error) => {
        console.error("Error updating product:", error);
        console.log("product unsuccessfull update",updateProduct)
        toast.error(getApiErrorMessage(error, "Failed to update product. Please try again."));
      }).finally(()=>{
        setLoading(false)
      }
      );
  };
 

  const handleChange = (e) => {
    const { name, value } = e.target;
    setUpdateProduct({
      ...updateProduct,
      [name]: value,
    });
  };


  const handleImageChange = (e) => {
    if (e.target.files && e.target.files[0]) {
      setImage(e.target.files[0]);
      setImageChanged(true); // Mark that user has selected a new image
    }
  };
  

  if (!product.id) {
    if (loadError) {
      return (
        <div className="container mt-5 pt-5">
          <div className="alert alert-danger" role="alert">
            {loadError}
          </div>
        </div>
      );
    }

    return (
      <div className="container mt-5 pt-5">
        <div className="d-flex justify-content-center align-items-center" style={{ height: "300px" }}>
          <div className="spinner-border text-primary" role="status">
            <span className="visually-hidden">Loading...</span>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="container mt-5 pt-5">
      <div className="row justify-content-center">
        <div className="col-md-10">
          <div className="card shadow">
            <div className="card-body">
              <h2 className="text-center mb-4">Update Product</h2>
              
              <form className={`row g-3 ${validated ? 'was-validated' : ''}`} noValidate onSubmit={handleSubmit}>
                <div className="col-md-6">
                  <label htmlFor="name" className="form-label fw-bold">Name</label>
                  <input
                    type="text"
                    className={`form-control ${validated && errors.name ? 'is-invalid' : ''}`}
                    placeholder={product.name}
                    value={updateProduct.name}
                    onChange={handleChange}
                    name="name"
                    id="name"
                    required
                  />
                  {errors.name && <div className="invalid-feedback">{errors.name}</div>}
                </div>
                
                <div className="col-md-6">
                  <label htmlFor="brand" className="form-label fw-bold">Brand</label>
                  <input
                    type="text"
                    name="brand"
                    className={`form-control ${validated && errors.brand ? 'is-invalid' : ''}`}
                    placeholder={product.brand}
                    value={updateProduct.brand}
                    onChange={handleChange}
                    id="brand"
                    required
                  />
                  {errors.brand && <div className="invalid-feedback">{errors.brand}</div>}
                </div>
                
                <div className="col-12">
                  <label htmlFor="description" className="form-label fw-bold">Description</label>
                  <textarea
                    className={`form-control ${validated && errors.description ? 'is-invalid' : ''}`}
                    placeholder={product.description}
                    value={updateProduct.description}
                    name="description"
                    onChange={handleChange}
                    id="description"
                    rows="3"
                    required
                  />
                  {errors.description && <div className="invalid-feedback">{errors.description}</div>}
                </div>
                
                <div className="col-md-4">
                  <label htmlFor="price" className="form-label fw-bold">Price</label>
                  <div className="input-group">
                    <span className="input-group-text">Rs</span>
                    <input
                      type="number"
                      className={`form-control ${validated && errors.price ? 'is-invalid' : ''}`}
                      onChange={handleChange}
                      value={updateProduct.price}
                      placeholder={product.price}
                      name="price"
                      id="price"
                      min="0.01"
                      step="0.01"
                      required
                    />
                    {errors.price && <div className="invalid-feedback">{errors.price}</div>}
                  </div>
                </div>
                
                <div className="col-md-4">
                  <label htmlFor="category" className="form-label fw-bold">Category</label>
                  <select
                    className={`form-select ${validated && errors.category ? 'is-invalid' : ''}`}
                    value={updateProduct.category}
                    onChange={handleChange}
                    name="category"
                    id="category"
                    required
                  >
                    <option value="">Select category</option>
                    <option value="Laptop">Laptop</option>
                    <option value="Headphone">Headphone</option>
                    <option value="Mobile">Mobile</option>
                    <option value="Electronics">Electronics</option>
                    <option value="Toys">Toys</option>
                    <option value="Fashion">Fashion</option>
                  </select>
                  {errors.category && <div className="invalid-feedback">{errors.category}</div>}
                </div>

                <div className="col-md-4">
                  <label htmlFor="stockQuantity" className="form-label fw-bold">Stock Quantity</label>
                  <input
                    type="number"
                    className={`form-control ${validated && errors.stockQuantity ? 'is-invalid' : ''}`}
                    onChange={handleChange}
                    placeholder={product.stockQuantity}
                    value={updateProduct.stockQuantity}
                    name="stockQuantity"
                    id="stockQuantity"
                    min="0"
                    required
                  />
                  {errors.stockQuantity && <div className="invalid-feedback">{errors.stockQuantity}</div>}
                </div>
                
                <div className="col-md-6">
                  <label htmlFor="releaseDate" className="form-label fw-bold">Release Date</label>
                  <input
                    type="date"
                    className={`form-control ${validated && errors.releaseDate ? 'is-invalid' : ''}`}
                    value={normalizeReleaseDateForInput(updateProduct.releaseDate)}
                    name="releaseDate"
                    onChange={handleChange}
                    id="releaseDate"
                    required
                  />
                  {errors.releaseDate && <div className="invalid-feedback">{errors.releaseDate}</div>}
                </div>
                
                <div className="col-md-6">
                  <label htmlFor="imageFile" className="form-label fw-bold">Image</label>
                  {image && (
                    <div className="mb-2">
                      <img
                        src={image ? URL.createObjectURL(image) : ""}
                        alt={product.name}
                        className="img-fluid rounded mb-2"
                        style={{ height: "150px", objectFit: "contain" }}
                      />
                    </div>
                  )}
                  <input
                    className={`form-control ${validated && errors.image ? 'is-invalid' : ''}`}
                    type="file"
                    onChange={handleImageChange}
                    id="imageFile"
                    accept="image/png, image/jpeg"
                  />
                  {errors.image && <div className="invalid-feedback">{errors.image}</div>}
                  <div className="form-text">Leave empty to keep current image</div>
                </div>
                
                {/* Product Available checkbox removed to match AddProduct UX */}

                <div className="col-12 mt-4">
                  {loading ? (
                    <button
                      className="btn btn-primary"
                      type="button"
                      disabled
                    >
                      <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                      Updating...
                    </button>
                  ) : (
                    <button type="submit" className="btn btn-primary">
                      Update Product
                    </button>
                  )}
                  <button 
                    type="button" 
                    className="btn btn-outline-secondary ms-2"
                    onClick={() => navigate('/')}
                  >
                    Cancel
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default UpdateProduct;