import React, { useState } from "react";
import Home from "./components/Home";
import Navbar from "./components/Navbar";
import Cart from "./components/Cart";
import AddProduct from "./components/AddProduct";
import Product from "./components/Product";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import UpdateProduct from "./components/UpdateProduct";
import Order from "./components/Order";
import AuthPage from "./components/AuthPage";
import OAuth2Redirect from "./components/OAuth2Redirect";
import ProtectedRoute from "./components/ProtectedRoute";

import SearchResults from "./components/SearchResults";
 
import "bootstrap/dist/css/bootstrap.min.css";
import "bootstrap/dist/js/bootstrap.bundle.min.js";
import { ToastContainer } from "react-toastify";

function App() {
  const [selectedCategory, setSelectedCategory] = useState("");

  const handleCategorySelect = (category) => {
    setSelectedCategory(category);
    console.log("Selected category:", category);
  };

  return (
    <BrowserRouter>
      <ToastContainer autoClose={2000}
        hideProgressBar={true} />
      <Navbar onSelectCategory={handleCategorySelect} />
      <main className="min-vh-100 bg-light" id="main-content">
        <Routes>
          <Route
            path="/"
            element={
              <Home selectedCategory={selectedCategory} />
            }
          />
          <Route
            path="/add_product"
            element={
              <ProtectedRoute requireAdmin>
                <AddProduct />
              </ProtectedRoute>
            }
          />
          <Route path="/product" element={<Product />} />
          <Route path="product/:id" element={<Product />} />
          <Route path="/cart" element={<Cart />} />
          <Route
            path="/product/update/:id"
            element={
              <ProtectedRoute requireAdmin>
                <UpdateProduct />
              </ProtectedRoute>
            }
          />
          <Route
            path="/orders"
            element={
              <ProtectedRoute requireAuth>
                <Order />
              </ProtectedRoute>
            }
          />
          <Route path="/login" element={<AuthPage />} />
          <Route path="/oauth2/redirect" element={<OAuth2Redirect />} />
          <Route path="/search-results" element={<SearchResults />} />
        </Routes>
      </main>
    </BrowserRouter>
  );
}

export default App;