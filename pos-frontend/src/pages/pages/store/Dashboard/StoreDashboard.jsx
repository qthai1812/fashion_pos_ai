
import { Outlet } from "react-router";
import { useDispatch, useSelector } from "react-redux";
import { useEffect } from "react";
import { getStoreByAdmin } from "../../../Redux Toolkit/features/store/storeThunks";
import StoreSidebar from "./StoreSidebar";
import StoreTopbar from "./StoreTopbar";

export default function StoreDashboard({ children }) {
  const dispatch = useDispatch();
  const { store } = useSelector((state) => state.store);

  useEffect(() => {
    if (localStorage.getItem("jwt")) {
      dispatch(getStoreByAdmin(localStorage.getItem("jwt")));
    }
  }, [dispatch]);

  if (store?.status === 'PENDING') {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-50">
        <div className="max-w-md p-8 bg-white rounded-lg shadow-lg text-center mx-4">
          <div className="w-16 h-16 bg-yellow-100 text-yellow-600 rounded-full flex items-center justify-center mx-auto mb-4">
            <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <h2 className="text-2xl font-bold text-gray-800 mb-2">Store Pending Approval</h2>
          <p className="text-gray-600 mb-6">
            Your store is currently under review by our super admins. You will be able to access your dashboard once it is approved.
          </p>
          <button onClick={() => {
            localStorage.clear();
            window.location.href = '/login';
          }} className="px-6 py-2 bg-primary text-white rounded-md hover:bg-primary/90 transition-colors">
            Back to Login
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-screen bg-gradient-to-br from-primary/5 via-background to-primary/10">
      <StoreSidebar />
      <div className="flex-1 flex flex-col">
        <StoreTopbar />
        <main className="flex-1 overflow-y-auto p-8 md:p-10 lg:p-12 bg-background/80 rounded-tl-3xl shadow-xl m-4">
          {children || <Outlet />}
        </main>
      </div>
    </div>
  );
}
