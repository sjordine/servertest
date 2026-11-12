import { NavLink, Route, Routes } from "react-router-dom";
import CategoriesPage from "./pages/CategoriesPage";
import EnvironmentCheckPage from "./pages/EnvironmentCheckPage";
import ProductFormPage from "./pages/ProductFormPage";
import ProductsPage from "./pages/ProductsPage";
import UnitsPage from "./pages/UnitsPage";

export default function App() {
  return (
    <>
      <nav className="topnav">
        <span className="brand">servertest</span>
        <NavLink to="/" end>Products</NavLink>
        <NavLink to="/categories">Categories</NavLink>
        <NavLink to="/units">Units</NavLink>
        <NavLink to="/health">Environment check</NavLink>
      </nav>
      <main>
        <Routes>
          <Route path="/" element={<ProductsPage />} />
          <Route path="/products/new" element={<ProductFormPage />} />
          <Route path="/products/:id" element={<ProductFormPage />} />
          <Route path="/categories" element={<CategoriesPage />} />
          <Route path="/units" element={<UnitsPage />} />
          <Route path="/health" element={<EnvironmentCheckPage />} />
          <Route path="*" element={<p>Page not found.</p>} />
        </Routes>
      </main>
    </>
  );
}
