package com.zosh.service.impl;

import com.zosh.domain.UserRole;
import com.zosh.modal.Customer;
import com.zosh.modal.Order;
import com.zosh.modal.Store;
import com.zosh.modal.User;
import com.zosh.repository.CustomerRepository;
import com.zosh.repository.OrderRepository;
import com.zosh.repository.StoreRepository;
import com.zosh.service.CustomerService;
import com.zosh.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final UserService userService;

    private Store resolveCurrentStore() {
        try {
            User user = userService.getCurrentUser();
            if (user == null || user.getRole() == UserRole.ROLE_ADMIN) {
                return null;
            }
            if (user.getStore() != null) {
                return user.getStore();
            }
            if (user.getBranch() != null && user.getBranch().getStore() != null) {
                return user.getBranch().getStore();
            }
            return storeRepository.findByStoreAdminId(user.getId());
        } catch (Exception e) {
            return null;
        }
    }

    private void assertCustomerBelongsToCurrentStore(Customer customer) throws Exception {
        User user = userService.getCurrentUser();
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        Store currentStore = resolveCurrentStore();
        if (currentStore == null) {
            throw new Exception("You do not belong to any active store");
        }
        if (customer.getStore() != null && !customer.getStore().getId().equals(currentStore.getId())) {
            throw new Exception("You do not have permission to access this customer");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Customer createCustomer(Customer customer) {
        if (customer.getCreatedAt() == null) {
            customer.setCreatedAt(LocalDateTime.now());
        }
        if (customer.getLoyaltyPoints() == null || customer.getLoyaltyPoints() < 0) {
            customer.setLoyaltyPoints(0);
        }
        if (customer.getStore() == null) {
            Store currentStore = resolveCurrentStore();
            if (currentStore == null && customer.getStoreId() != null) {
                currentStore = storeRepository.findById(customer.getStoreId()).orElse(null);
            }
            customer.setStore(currentStore);
        }
        customer.setUpdatedAt(LocalDateTime.now());
        return customerRepository.save(customer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Customer updateCustomer(Long id, Customer customer) throws Exception {
        Customer customerToUpdate = customerRepository.findByIdWithLock(id).orElseThrow(
                () -> new Exception("Customer not found")
        );
        assertCustomerBelongsToCurrentStore(customerToUpdate);
        if (customerToUpdate.getStore() == null) {
            customerToUpdate.setStore(resolveCurrentStore());
        }
        if (customer.getFullName() != null && !customer.getFullName().trim().isEmpty()) {
            customerToUpdate.setFullName(customer.getFullName().trim());
        }
        if (customer.getEmail() != null) {
            customerToUpdate.setEmail(customer.getEmail().trim());
        }
        if (customer.getPhone() != null && !customer.getPhone().trim().isEmpty()) {
            customerToUpdate.setPhone(customer.getPhone().trim());
        }
        if (customer.getLoyaltyPoints() != null) {
            customerToUpdate.setLoyaltyPoints(Math.max(0, customer.getLoyaltyPoints()));
        }
        customerToUpdate.setUpdatedAt(LocalDateTime.now());
        return customerRepository.save(customerToUpdate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCustomer(Long id) throws Exception {
        Customer customerToDelete = customerRepository.findById(id).orElseThrow(
                () -> new Exception("Customer not found")
        );
        assertCustomerBelongsToCurrentStore(customerToDelete);
        List<Order> customerOrders = orderRepository.findByCustomerId(id);
        for (Order order : customerOrders) {
            order.setCustomer(null);
            orderRepository.save(order);
        }
        customerRepository.delete(customerToDelete);
    }

    @Override
    @Transactional(readOnly = true)
    public Customer getCustomer(Long id) throws Exception {
        Customer customer = customerRepository.findById(id).orElseThrow(
                () -> new Exception("Customer not found")
        );
        assertCustomerBelongsToCurrentStore(customer);
        return customer;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> getAllCustomers() throws Exception {
        Store currentStore = resolveCurrentStore();
        if (currentStore != null) {
            return customerRepository.findByStoreId(currentStore.getId());
        }
        return customerRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> searchCustomers(String keyword) throws Exception {
        Store currentStore = resolveCurrentStore();
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllCustomers();
        }
        if (currentStore != null) {
            return customerRepository.searchCustomersByStore(currentStore.getId(), keyword.trim());
        }
        return customerRepository.searchCustomers(keyword.trim());
    }
}
