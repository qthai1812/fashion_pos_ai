import React from "react";
import { useSelector } from "react-redux";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { EmployeeForm } from "../../store/Employee";
import { Plus, Download } from "lucide-react";

export const AddEmployeeDialog = ({
  isAddDialogOpen,
  setIsAddDialogOpen,
  handleAddEmployee,
  roles,
}) => (
  <Dialog open={isAddDialogOpen} onOpenChange={setIsAddDialogOpen}>
    <DialogTrigger asChild>
      <Button className="bg-emerald-600 hover:bg-emerald-700">
        <Plus className="mr-2 h-4 w-4" /> Add Employee
      </Button>
    </DialogTrigger>
    <DialogContent>
      <DialogHeader>
        <DialogTitle>Add New Employee</DialogTitle>
      </DialogHeader>
      <EmployeeForm
        initialData={null}
        onSubmit={handleAddEmployee}
        roles={roles}
      />
    </DialogContent>
  </Dialog>
);

export const EditEmployeeDialog = ({
  isEditDialogOpen,
  setIsEditDialogOpen,
  selectedEmployee,
  handleEditEmployee,
  roles,
}) =>
  selectedEmployee && (
    <Dialog open={isEditDialogOpen} onOpenChange={setIsEditDialogOpen}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit Employee</DialogTitle>
        </DialogHeader>
        <EmployeeForm
          initialData={
            selectedEmployee
              ? {
                  ...selectedEmployee,
                  branchId: selectedEmployee.branchId || "",
                }
              : null
          }
          onSubmit={handleEditEmployee}
          roles={roles}
        />
      </DialogContent>
    </Dialog>
  );

export const ResetPasswordDialog = ({
  isResetPasswordDialogOpen,
  setIsResetPasswordDialogOpen,
  selectedEmployee,
  handleResetPassword,
}) =>
  selectedEmployee && (
    <Dialog
      open={isResetPasswordDialogOpen}
      onOpenChange={setIsResetPasswordDialogOpen}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Reset Password</DialogTitle>
        </DialogHeader>
        <div className="py-4">
          <p>
            Are you sure you want to reset the password for{" "}
            <strong>
              {selectedEmployee.fullName || selectedEmployee.name}
            </strong>
            ?
          </p>
          <p className="text-sm text-gray-500 mt-2">
            A temporary password will be generated and applied to their account.
          </p>
        </div>
        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => setIsResetPasswordDialogOpen(false)}
          >
            Cancel
          </Button>
          <Button onClick={handleResetPassword}>Reset Password</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );

export const PerformanceDialog = ({
  isPerformanceDialogOpen,
  setIsPerformanceDialogOpen,
  selectedEmployee,
}) => {
  const { orders = [] } = useSelector((state) => state.order || {});
  const employeeOrders = (orders || []).filter(
    (o) =>
      o.cashierId === selectedEmployee?.id ||
      o.cashier?.id === selectedEmployee?.id,
  );
  const ordersProcessed = employeeOrders.length;
  const totalSales = employeeOrders.reduce(
    (sum, o) => sum + (Number(o.totalAmount) || 0),
    0,
  );
  const avgOrderValue =
    ordersProcessed > 0 ? Math.round(totalSales / ordersProcessed) : 0;

  const handleExportEmployeeReport = () => {
    if (!selectedEmployee) return;
    const empName =
      selectedEmployee.fullName || selectedEmployee.name || "Employee";
    const csvContent =
      "data:text/csv;charset=utf-8," +
      [
        "Employee ID,Full Name,Email,Role,Metric,Value",
        `${selectedEmployee.id},"${empName}","${selectedEmployee.email}","${selectedEmployee.role}","Orders Processed (30d)",${ordersProcessed}`,
        `${selectedEmployee.id},"${empName}","${selectedEmployee.email}","${selectedEmployee.role}","Total Sales (30d)",${totalSales}`,
        `${selectedEmployee.id},"${empName}","${selectedEmployee.email}","${selectedEmployee.role}","Avg. Order Value (30d)",${avgOrderValue}`,
      ].join("\n");
    const link = document.createElement("a");
    link.setAttribute("href", encodeURI(csvContent));
    link.setAttribute("download", `performance_${selectedEmployee.id}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    selectedEmployee && (
      <Dialog
        open={isPerformanceDialogOpen}
        onOpenChange={setIsPerformanceDialogOpen}
      >
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>
              Performance Summary -{" "}
              {selectedEmployee.fullName || selectedEmployee.name}
            </DialogTitle>
          </DialogHeader>
          <div className="py-4">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <Card>
                <CardContent className="p-6 text-center">
                  <h3 className="text-sm font-medium text-gray-500">
                    Orders Processed
                  </h3>
                  <p className="text-3xl font-bold mt-2">{ordersProcessed}</p>
                  <p className="text-xs text-gray-500 mt-1">Last 30 days</p>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="p-6 text-center">
                  <h3 className="text-sm font-medium text-gray-500">
                    Total Sales
                  </h3>
                  <p className="text-3xl font-bold mt-2">
                    đ{totalSales.toLocaleString()}
                  </p>
                  <p className="text-xs text-gray-500 mt-1">Last 30 days</p>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="p-6 text-center">
                  <h3 className="text-sm font-medium text-gray-500">
                    Avg. Order Value
                  </h3>
                  <p className="text-3xl font-bold mt-2">
                    đ{avgOrderValue.toLocaleString()}
                  </p>
                  <p className="text-xs text-gray-500 mt-1">Last 30 days</p>
                </CardContent>
              </Card>
            </div>
          </div>
          <DialogFooter>
            <Button onClick={() => setIsPerformanceDialogOpen(false)}>
              Close
            </Button>
            <Button variant="outline" onClick={handleExportEmployeeReport}>
              <Download className="h-4 w-4 mr-2" />
              Export Report
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    )
  );
};
