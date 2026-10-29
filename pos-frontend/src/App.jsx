import React, { useEffect, useState } from "react";
import { Routes, Route, Navigate } from "react-router";
import { useDispatch, useSelector } from "react-redux";
import AuthRoutes from "./routes/AuthRoutes";
import StoreRoutes from "./routes/StoreRoutes";
import BranchManagerRoutes from "./routes/BranchManagerRoutes";
import { getUserProfile } from "./Redux Toolkit/features/user/userThunks";
import Landing from "./pages/common/Landing/Landing";
import CashierRoutes from "./routes/CashierRoutes";
import Onboarding from "./pages/onboarding/Onboarding";
import {
  getStoreByAdmin,
  getStoreByEmployee,
} from "./Redux Toolkit/features/store/storeThunks";
import SuperAdminRoutes from "./routes/SuperAdminRoutes";
import PageNotFound from "./pages/common/PageNotFound";

const App = () => {
  const dispatch = useDispatch();
  const { userProfile, loading: userLoading } = useSelector(
    (state) => state.user,
  );
  const { store, loading: storeLoading } = useSelector((state) => state.store);
  const [storeChecked, setStoreChecked] = useState(false);

  useEffect(() => {
    const jwt = localStorage.getItem("jwt");
    if (jwt) {
      dispatch(getUserProfile(jwt));
    }
  }, [dispatch]);

  useEffect(() => {
    if (!userProfile) return;
    if (userProfile.role === "ROLE_STORE_ADMIN") {
      dispatch(getStoreByAdmin()).finally(() => setStoreChecked(true));
    } else if (userProfile.role === "ROLE_STORE_MANAGER") {
      dispatch(getStoreByEmployee()).finally(() => setStoreChecked(true));
    } else {
      setStoreChecked(true);
    }
  }, [dispatch, userProfile]);

  if (userLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        Loading...
      </div>
    );
  }

  if (userProfile && userProfile.role) {
    if (userProfile.role === "ROLE_ADMIN") {
      return (
        <Routes>
          <Route path="/" element={<Navigate to="/super-admin" replace />} />
          <Route path="/super-admin/*" element={<SuperAdminRoutes />} />
          <Route path="*" element={<PageNotFound />} />
        </Routes>
      );
    }

    if (userProfile.role === "ROLE_BRANCH_CASHIER") {
      return (
        <Routes>
          <Route path="/" element={<Navigate to="/cashier" replace />} />
          <Route path="/cashier/*" element={<CashierRoutes />} />
          <Route path="*" element={<PageNotFound />} />
        </Routes>
      );
    }

    if (
      userProfile.role === "ROLE_STORE_ADMIN" ||
      userProfile.role === "ROLE_STORE_MANAGER"
    ) {
      if (!storeChecked || storeLoading) {
        return (
          <div className="flex items-center justify-center min-h-screen">
            Loading store...
          </div>
        );
      }

      if (!store || !store.id) {
        return (
          <Routes>
            <Route
              path="/"
              element={<Navigate to="/auth/onboarding" replace />}
            />
            <Route path="/auth/onboarding" element={<Onboarding />} />
            <Route
              path="*"
              element={<Navigate to="/auth/onboarding" replace />}
            />
          </Routes>
        );
      }

      return (
        <Routes>
          <Route path="/" element={<Navigate to="/store" replace />} />
          <Route path="/store/*" element={<StoreRoutes />} />
          <Route path="*" element={<PageNotFound />} />
        </Routes>
      );
    }

    if (
      userProfile.role === "ROLE_BRANCH_MANAGER" ||
      userProfile.role === "ROLE_BRANCH_ADMIN"
    ) {
      return (
        <Routes>
          <Route path="/" element={<Navigate to="/branch" replace />} />
          <Route path="/branch/*" element={<BranchManagerRoutes />} />
          <Route path="*" element={<PageNotFound />} />
        </Routes>
      );
    }

    return (
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="*" element={<PageNotFound />} />
      </Routes>
    );
  }

  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/auth/*" element={<AuthRoutes />} />
      <Route path="*" element={<PageNotFound />} />
    </Routes>
  );
};

export default App;
