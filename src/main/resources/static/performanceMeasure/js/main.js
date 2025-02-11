/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

import {testDetailsManager} from './components/TestDetailsManager.js';
import {metricsService} from './core/MetricsSSEService.js';
import {
  formatNumber,
  escapeHtml,
  getStatusText,
  getStatusClass
} from './utils/formatters.js';

/**
 * MainManager class for handling core application functionality.
 * Manages:
 * - Endpoint selection and display
 * - Test configuration and execution
 * - Results visualization
 * - User interface interactions
 */
class MainManager {
  constructor() {
    this.endpointsData = {};
    this.selectedEndpoint = null;
    this.editor = null;
    this.pollCount = 0;
    this.MAX_POLLS = 60;
    this.isTestRunning = false;
  }

  /**
   * Initializes the application components and loads initial data.
   * Sets up JSON editor, event listeners, and loads endpoint data.
   */
  async initialize() {
    this.initializeJsonEditor();
    this.setupEventListeners();
    await this.loadInitialData();
  }

  /**
   * Initializes the JSON editor for request body configuration.
   */
  initializeJsonEditor() {
    const container = document.getElementById('jsonEditor');
    this.editor = new window.JSONEditor(container, {
      mode: 'code',
      statusBar: false,
      mainMenuBar: false
    });
    this.editor.set({});
  }

  /**
   * Sets up event listeners for dynamic header management and form submission.
   */
  setupEventListeners() {
    // Add/delete header event
    document.addEventListener('click', this.handleHeaderRowClick.bind(this));

    // Submit form event
    document.getElementById('testForm').addEventListener('submit',
        this.handleFormSubmit.bind(this));
  }

  /**
   * Handles adding and removing header rows in the test configuration form
   */
  handleHeaderRowClick(e) {
    if (e.target.matches('.add-header')) {
      const headerRow = e.target.closest('.header-row');
      const newRow = headerRow.cloneNode(true);
      newRow.querySelectorAll('input').forEach(input => input.value = '');
      headerRow.parentNode.insertBefore(newRow, headerRow.nextSibling);
    } else if (e.target.matches('.remove-header')) {
      const headerRow = e.target.closest('.header-row');
      if (document.querySelectorAll('.header-row').length > 1) {
        headerRow.remove();
      }
    }
  }

  /**
   * Loads initial endpoint data and test results from the server.
   * Initializes the endpoint view with the loaded data.
   */
  async loadInitialData() {
    try {
      const response = await fetch('/performanceMeasure/endpoints');
      if (response.ok) {
        this.endpointsData = await response.json();
        this.initializeEndpointView();
      }

      const resultsResponse = await fetch('/performanceMeasure/results');
      if (resultsResponse.ok) {
        const results = await resultsResponse.json();
        results.forEach(result => this.updateResultsTable(result));
      }
    } catch (error) {
      console.error('Failed to load initial data:', error);
      this.showError('Failed to load test endpoints');
    }
  }

  /**
   * Handles test form submission.
   * Validates input and initiates performance test execution.
   * @param {Event} e - Form submission event
   */
  async handleFormSubmit(e) {
    e.preventDefault();

    if (this.isTestRunning) {
      this.showError('Test is already running');
      return;
    }

    if (!this.selectedEndpoint) {
      this.showError('Please select an endpoint first');
      return;
    }

    let jsonBody;
    try {
      jsonBody = this.editor.get();
    } catch (error) {
      this.showError('Invalid JSON in request body');
      return;
    }

    const requestData = {
      description: document.getElementById('description').value,
      url: window.location.origin + this.selectedEndpoint.endpointUrl,
      method: this.selectedEndpoint.httpMethod,
      requestBody: JSON.stringify(jsonBody),
      headers: this.getHeaders(),
      concurrentUsers: parseInt(
          document.getElementById('concurrentUsers').value),
      repeatCount: parseInt(document.getElementById('repeatCount').value),
      rampUpSeconds: parseInt(document.getElementById('rampUpSeconds').value)
    };

    try {
      this.isTestRunning = true;
      const response = await fetch('/performanceMeasure/run', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(requestData)
      });

      if (response.ok) {
        const testId = await response.text();
        this.showSuccess('Test started successfully');

        metricsService.connect(testId);
        await this.pollTestStatus(testId);
      } else {
        throw new Error('Failed to start test');
      }
    } catch (error) {
      this.showError('Error starting test: ' + error.message);
    } finally {
      this.isTestRunning = false;
    }
  }

  /**
   * Initializes the endpoint view with card and list displays.
   * Sets up search and filtering functionality.
   */
  initializeEndpointView() {
    const cardsContainer = document.getElementById('endpointCardsView');
    const listBody = document.getElementById('endpointListBody');

    cardsContainer.innerHTML = '';
    listBody.innerHTML = '';

    // Sort endPoint
    const sortedMethods = Object.keys(this.endpointsData).sort();

    sortedMethods.forEach(method => {
      this.endpointsData[method].forEach((endpoint, index) => {

        this.addEndpointCard(endpoint, method, index);
        this.addEndpointListItem(endpoint, method, index);
      });
    });

    this.setupSearchAndFilter();
  }

  /**
   * Sets up search and filtering functionality for endpoints
   */
  setupSearchAndFilter() {
    const searchInput = document.getElementById('endpointSearch');
    const methodFilter = document.getElementById('methodFilter');

    const filterEndpoints = () => {
      const searchTerm = searchInput.value.toLowerCase();
      const selectedMethod = methodFilter.value;

      const cards = document.querySelectorAll('.endpoint-card');
      const rows = document.querySelectorAll('#endpointListBody tr');

      [...cards, ...rows].forEach(element => {
        const id = element.dataset.endpointId;
        const [method, index] = id.split('-');
        const endpoint = this.endpointsData[method][index];

        const matchesSearch = endpoint.endpointUrl.toLowerCase().includes(
                searchTerm) ||
            endpoint.controllerClassName.toLowerCase().includes(searchTerm);
        const matchesMethod = !selectedMethod || method === selectedMethod;

        element.style.display = (matchesSearch && matchesMethod) ? '' : 'none';
      });
    };

    searchInput.addEventListener('input', filterEndpoints);
    methodFilter.addEventListener('change', filterEndpoints);
  }

  /**
   * Creates and adds a card view representation of an endpoint
   */
  addEndpointCard(endpoint, method, index) {
    const template = document.getElementById('endpointCardTemplate');
    const card = template.content.cloneNode(true);

    const cardDiv = card.querySelector('.endpoint-card');
    cardDiv.dataset.endpointId = `${method}-${index}`;

    const badge = card.querySelector('.method-badge');
    badge.textContent = method;
    badge.classList.add(`method-${method}`);

    const title = card.querySelector('.card-title');
    title.textContent = endpoint.endpointUrl;

    const controller = card.querySelector('.card-text');
    controller.textContent = `${endpoint.controllerClassName}.${endpoint.controllerMethodName}`;

    const servicesList = card.querySelector('.services-list');
    if (endpoint.annotatedServices && endpoint.annotatedServices.length > 0) {
      endpoint.annotatedServices.forEach(service => {
        const serviceItem = document.createElement('div');
        serviceItem.className = 'service-item small';
        serviceItem.innerHTML = `
                <div class="fw-bold">${service.serviceClassName}.${service.methodName}</div>
                <small class="text-muted">${service.description
        || 'No description'}</small>
            `;
        servicesList.appendChild(serviceItem);
      });
    } else {
      const emptyItem = document.createElement('div');
      emptyItem.className = 'service-item small text-muted';
      emptyItem.textContent = 'No annotated services';
      servicesList.appendChild(emptyItem);
    }

    document.getElementById('endpointCardsView').appendChild(card);
  }

  /**
   * Creates and adds a list view representation of an endpoint
   */
  addEndpointListItem(endpoint, method, index) {
    const tr = document.createElement('tr');
    tr.dataset.endpointId = `${method}-${index}`;

    tr.innerHTML = `
        <td><span class="badge method-badge method-${method}">${method}</span></td>
        <td>${endpoint.endpointUrl}</td>
        <td>${endpoint.controllerClassName}.${endpoint.controllerMethodName}</td>
        <td>${endpoint.annotatedServices?.length || 0} services</td>
        <td>
            <button class="btn btn-sm btn-primary" onclick="selectEndpoint(closest('tr'))">
                Select
            </button>
        </td>
    `;

    document.getElementById('endpointListBody').appendChild(tr);
  }

  /**
   * Handles endpoint selection and updates UI accordingly
   */
  selectEndpoint(element) {

    document.querySelectorAll('.selected-endpoint').forEach(el =>
        el.classList.remove('selected-endpoint'));

    element.classList.add('selected-endpoint');
    const [method, index] = element.dataset.endpointId.split('-');
    this.selectedEndpoint = {
      ...this.endpointsData[method][index],
      httpMethod: method
    };

    console.log('Selected Endpoint:', this.selectedEndpoint); // 디버깅용

    // Update Ui
    const methodEl = document.getElementById('selectedEndpointMethod');
    const urlEl = document.getElementById('selectedEndpointUrl');
    const descEl = document.getElementById('endpoint-description');
    const requestTypeEl = document.getElementById('requestType');
    const requestBodySection = document.getElementById('requestBodySection');

    if (methodEl) {
      methodEl.textContent = this.selectedEndpoint.httpMethod;
    }
    if (urlEl) {
      urlEl.textContent = this.selectedEndpoint.endpointUrl;
    }
    if (descEl) {
      descEl.textContent = this.selectedEndpoint.description || '-';
    }
    if (requestTypeEl) {
      requestTypeEl.textContent = this.selectedEndpoint.requestType;
    }

    // Show/hide Request Body
    if (this.selectedEndpoint.httpMethod === 'POST' ||
        this.selectedEndpoint.httpMethod === 'PUT' ||
        (this.selectedEndpoint.requestType &&
            this.selectedEndpoint.requestType.toLowerCase() !== 'void')) {
      requestBodySection.style.display = 'block';
      if (this.editor) {
        if (this.selectedEndpoint.requestExample &&
            Object.keys(this.selectedEndpoint.requestExample).length > 0) {
          console.log('Setting request example:',
              this.selectedEndpoint.requestExample);
          this.editor.set(this.selectedEndpoint.requestExample);
        } else {
          console.log('No request example available, setting empty object');
          this.editor.set({
            id: 0,
            name: "example"
          });
        }
      }
    } else {
      console.log('Hiding request body section for endpoint:',
          this.selectedEndpoint);
      requestBodySection.style.display = 'none';
    }

    // Active Run Test button
    const runTestBtn = document.getElementById('runTestBtn');
    if (runTestBtn) {
      runTestBtn.disabled = false;
    }
  }

  /**
   * Toggles between card and list view for endpoints
   */
  toggleView(viewType) {
    const cardView = document.getElementById('endpointCardsView');
    const listView = document.getElementById('endpointListView');

    if (viewType === 'card') {
      cardView.classList.remove('d-none');
      listView.classList.add('d-none');
    } else {
      cardView.classList.add('d-none');
      listView.classList.remove('d-none');
    }
  }

  /**
   * Collects all configured headers from the form
   */
  getHeaders() {
    const headers = {};
    document.querySelectorAll('.header-row').forEach(row => {
      const keyInput = row.querySelector('input:first-of-type');
      const valueInput = row.querySelector('input:last-of-type');

      if (keyInput && valueInput) {
        const key = keyInput.value.trim();
        const value = valueInput.value.trim();
        if (key && value) {
          headers[key] = value;
        }
      }
    });
    return headers;
  }

  showError(message) {
    this.showAlert(message, 'danger');
  }

  showSuccess(message) {
    this.showAlert(message, 'success');
  }

  /**
   * Shows alert message with specified type
   */
  showAlert(message, type) {
    const alertDiv = document.createElement('div');
    alertDiv.className = `alert alert-${type} alert-dismissible fade show`;
    alertDiv.innerHTML = `
            ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
        `;
    document.querySelector('.container').insertBefore(alertDiv,
        document.querySelector('.card'));
    setTimeout(() => alertDiv.remove(), 5000);
  }

  /**
   * Clears all test results from the display
   */
  clearResults() {
    document.getElementById('resultsBody').innerHTML = '';
  }

  /**
   * Polls test status at regular intervals.
   * Updates UI with current test progress.
   * @param {string} testId - ID of the test to monitor
   */
  async pollTestStatus(testId) {
    try {
      if (this.pollCount++ > this.MAX_POLLS) {
        this.showError("Test timed out");
        return;
      }

      const response = await fetch(`/performanceMeasure/status/${testId}`);
      if (!response.ok) {
        throw new Error('Network response was not ok');
      }

      const status = await response.json();
      this.updateResultsTable(status);

      if (!status.completed) {
        setTimeout(() => this.pollTestStatus(testId), 1000);
      } else {
        this.showSuccess('Test completed successfully');
      }
    } catch (error) {
      this.showError(`Error: ${error.message}`);
    }
  }

  /**
   * Updates the test results table with new test data.
   * Handles different test statuses and updates UI accordingly.
   * @param {Object} status - Current test status data
   */
  updateResultsTable(status) {
    const tbody = document.getElementById('resultsBody');
    let row = tbody.querySelector(`tr[data-test-id="${status.testId}"]`);

    if (!row) {
      row = document.createElement('tr');
      row.setAttribute('data-test-id', status.testId);
      tbody.insertBefore(row, tbody.firstChild);
    }

    const statusText = getStatusText(status);
    const statusClass = getStatusClass(status);

    row.className = statusClass;
    row.innerHTML = `
            <td>${escapeHtml(status.description || '')}</td>
            <td>${escapeHtml(status.url || '')}</td>
            <td>${statusText}</td>
            <td>${formatNumber(status.averageResponseTime)} ms</td>
            <td>${formatNumber(status.maxResponseTime)} ms</td>
            <td>${formatNumber(status.requestsPerSecond)}</td>
            <td>${formatNumber(status.errorRate)}%</td>
            <td>
                <button class="btn btn-sm btn-info" onclick="showDetails('${status.testId}')">Details</button>
            </td>
        `;
  }
}

export const mainManager = new MainManager();

window.mainManager = mainManager;
window.showDetails = (testId) => testDetailsManager.showDetails(testId);
window.selectEndpoint = (element) => mainManager.selectEndpoint(element);
window.toggleView = (viewType) => mainManager.toggleView(viewType);
window.clearResults = () => mainManager.clearResults();

window.addEventListener('load', () => mainManager.initialize());