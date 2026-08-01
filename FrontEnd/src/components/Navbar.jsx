import React, { useEffect, useRef, useState, useContext } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import API from "../axios";
import { getApiErrorMessage } from "../utils/apiError";
import AppContext from "../Context/Context";

const Navbar = () => {
  const getInitialTheme = () => {
    const storedTheme = localStorage.getItem("theme");
    return storedTheme ? storedTheme : "light-theme";
  };
  
  const [theme] = useState(getInitialTheme());
  const [input, setInput] = useState("");
  const [showNoProductsMessage, setShowNoProductsMessage] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [searchMessage, setSearchMessage] = useState("");
  const [showUserMenu, setShowUserMenu] = useState(false);

  const [isNavCollapsed, setIsNavCollapsed] = useState(true);
  const navbarRef = useRef(null);
  
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated, logout, userId, isAdmin, refreshData } = useContext(AppContext);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (navbarRef.current && !navbarRef.current.contains(event.target)) {
        setIsNavCollapsed(true);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);

    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, []);

  const handleNavbarToggle = () => {
    setIsNavCollapsed(!isNavCollapsed);
  };

  const handleLinkClick = () => {
    setIsNavCollapsed(true);
  };

  const handleHomeClick = () => {
    handleLinkClick();

    if (location.pathname === "/") {
      refreshData();
    }
  };

  const handleLogout = () => {
    setShowUserMenu(false);
    logout();
    handleLinkClick();
    navigate("/login");
  };

  const handleAuthClick = () => {
    handleLinkClick();
    navigate("/login");
  };

  // Update input value without searching
  const handleInputChange = (value) => {
    setInput(value);
  };

  // Only search when the form is submitted
  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (input.trim() === "") return;
    
    setShowNoProductsMessage(false);
    setSearchMessage("");
    setIsLoading(true);
    setIsNavCollapsed(true);
    
    try {
      const response = await API.get(`/api/product/search?keyword=${encodeURIComponent(input)}`);
      
      if (response.data.length === 0) {
        setShowNoProductsMessage(true);
        setSearchMessage("No products found matching your search.");
      } else {
        navigate(`/search-results`, { state: { searchData: response.data } });
      }
      
      console.log("Search results:", response.data);
    } catch (error) {
      console.error("Error searching:", error);
      setShowNoProductsMessage(true);
      setSearchMessage(getApiErrorMessage(error, "Search failed"));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    document.body.className = theme;
  }, [theme]);

  return (
    <nav className="navbar navbar-expand-lg fixed-top bg-white shadow-sm" ref={navbarRef}>
      <div className="container-fluid">
        <Link className="navbar-brand" to="/" onClick={handleHomeClick}>
          Ecom
        </Link>
        <button
  className="navbar-toggler"
  type="button"
  onClick={handleNavbarToggle}
  aria-controls="navbarSupportedContent"
  aria-expanded={!isNavCollapsed}
  aria-label="Toggle navigation"
>
  <span className="navbar-toggler-icon"></span>
</button>
        <div
          className={`${isNavCollapsed ? 'collapse' : ''} navbar-collapse`}
          id="navbarSupportedContent"
        >
          <ul className="navbar-nav me-auto mb-2 mb-lg-0">
            <li className="nav-item">
              <Link className="nav-link active" aria-current="page" to="/" onClick={handleHomeClick}>
                Home
              </Link>
            </li>
            {isAdmin && (
              <li className="nav-item">
                <Link className="nav-link" to="/add_product" onClick={handleLinkClick}>
                  Add Product
                </Link>
              </li>
            )}

            {isAuthenticated && (
              <li className="nav-item">
                <Link className="nav-link" to="/orders" onClick={handleLinkClick}>
                  Orders
                </Link>
              </li>
            )}
      

          </ul>
          
         
          
          <div className="d-flex align-items-center">
            <Link to="/cart" className="nav-link text-dark me-3" onClick={handleLinkClick}>
              <i className="bi bi-cart me-1"></i>
              Cart
            </Link>
            {!isAuthenticated ? (
              <button type="button" className="btn btn-outline-primary me-3" onClick={handleAuthClick}>
                Login / Signup
              </button>
            ) : (
              <div className="position-relative me-3">
                <button
                  type="button"
                  className="btn btn-outline-success"
                  onClick={() => setShowUserMenu((current) => !current)}
                >
                  Logged in
                </button>

                {showUserMenu && (
                  <div
                    className="position-absolute end-0 mt-2 bg-white border rounded shadow-sm p-2"
                    style={{ minWidth: "160px", zIndex: 1100 }}
                  >
                    <button
                      type="button"
                      className="btn btn-danger btn-sm w-100"
                      onClick={handleLogout}
                    >
                      Sign Out
                    </button>
                  </div>
                )}
              </div>
            )}
            <form className="d-flex" role="search" onSubmit={handleSubmit} id="searchForm">
              <input
                className="form-control me-2"
                type="search"
                placeholder="Type to search"
                aria-label="Search"
                value={input}
                onChange={(e) => handleInputChange(e.target.value)}
              />
              {isLoading ? (
                <button
                  className="btn btn-outline-success"
                  type="button"
                  disabled
                >
                  <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                  <span className="visually-hidden">Loading...</span>
                </button>
              ) : (
                <button
                  className="btn btn-outline-success"
                  type="submit"
                >
                  Search
                </button>
              )}
            </form>
            
            {showNoProductsMessage && (
              <div className="alert alert-warning position-absolute mt-2" style={{ top: "100%", zIndex: 1000 }}>
                {searchMessage || "No products found matching your search."}
              </div>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
};

export default Navbar;