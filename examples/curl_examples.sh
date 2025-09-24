#!/bin/bash
#
# SecurePaste curl API Examples
#
# This script demonstrates how to interact with the SecurePaste API
# using curl commands from the command line.
#
# Prerequisites:
# - curl must be installed
# - jq (JSON processor) is recommended but optional
# - SecurePaste service should be running on localhost:8080

set -e  # Exit on any error

# Configuration
BASE_URL="${SECUREPASTE_URL:-http://localhost:8080}"
API_URL="${BASE_URL}/api/pastes"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
print_section() {
    echo -e "\n${BLUE}=== $1 ===${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

# Function to pretty print JSON if jq is available
print_json() {
    if command -v jq &> /dev/null; then
        echo "$1" | jq .
    else
        echo "$1"
    fi
}

# Function to extract value from JSON response
extract_json_value() {
    local json="$1"
    local key="$2"
    if command -v jq &> /dev/null; then
        echo "$json" | jq -r ".$key"
    else
        # Fallback without jq (basic extraction)
        echo "$json" | grep -o "\"$key\":\"[^\"]*" | cut -d'"' -f4
    fi
}

print_section "SecurePaste curl API Examples"

# Health Check
print_section "1. Health Check"
echo "curl -s \"${API_URL}/health\""
health_response=$(curl -s "${API_URL}/health")
if [ $? -eq 0 ]; then
    print_success "Service is responding"
    print_json "$health_response"
else
    print_error "Service is not available"
    exit 1
fi

# Create a simple paste
print_section "2. Creating a Simple Paste"
simple_paste_data='{
    "title": "Hello World from curl",
    "content": "echo \"Hello, World!\"\necho \"This is a test paste from curl\"",
    "language": "bash",
    "authorName": "curl Example",
    "visibility": "PUBLIC"
}'

echo "curl -X POST \"${API_URL}\" \\"
echo "  -H \"Content-Type: application/json\" \\"
echo "  -d '${simple_paste_data}'"

simple_paste_response=$(curl -s -X POST "${API_URL}" \
  -H "Content-Type: application/json" \
  -d "$simple_paste_data")

if [ $? -eq 0 ]; then
    simple_paste_id=$(extract_json_value "$simple_paste_response" "id")
    if [ "$simple_paste_id" != "null" ] && [ -n "$simple_paste_id" ]; then
        print_success "Simple paste created with ID: $simple_paste_id"
        print_json "$simple_paste_response"
    else
        print_error "Failed to create simple paste"
        print_json "$simple_paste_response"
        exit 1
    fi
else
    print_error "Failed to create simple paste"
    exit 1
fi

# Create a password-protected paste
print_section "3. Creating a Password-Protected Paste"
protected_paste_data='{
    "title": "Secret Configuration",
    "content": "# Secret configuration file\napi_key=secret_key_12345\ndatabase_url=postgresql://user:pass@localhost/db\n# Do not share this!",
    "language": "yaml",
    "visibility": "UNLISTED",
    "password": "secret123"
}'

echo "curl -X POST \"${API_URL}\" \\"
echo "  -H \"Content-Type: application/json\" \\"
echo "  -d '${protected_paste_data}'"

protected_paste_response=$(curl -s -X POST "${API_URL}" \
  -H "Content-Type: application/json" \
  -d "$protected_paste_data")

if [ $? -eq 0 ]; then
    protected_paste_id=$(extract_json_value "$protected_paste_response" "id")
    if [ "$protected_paste_id" != "null" ] && [ -n "$protected_paste_id" ]; then
        print_success "Protected paste created with ID: $protected_paste_id"
        print_json "$protected_paste_response"
    else
        print_error "Failed to create protected paste"
        print_json "$protected_paste_response"
        exit 1
    fi
else
    print_error "Failed to create protected paste"
    exit 1
fi

# Create a paste with expiration
print_section "4. Creating a Paste with Expiration (60 minutes)"
expiring_paste_data='{
    "title": "Temporary Code Snippet",
    "content": "// This code will be automatically deleted after 60 minutes\nconst temp = \"temporary data\";\nconsole.log(temp);",
    "language": "javascript",
    "visibility": "PUBLIC",
    "expirationMinutes": 60
}'

echo "curl -X POST \"${API_URL}\" \\"
echo "  -H \"Content-Type: application/json\" \\"
echo "  -d '${expiring_paste_data}'"

expiring_paste_response=$(curl -s -X POST "${API_URL}" \
  -H "Content-Type: application/json" \
  -d "$expiring_paste_data")

if [ $? -eq 0 ]; then
    expiring_paste_id=$(extract_json_value "$expiring_paste_response" "id")
    print_success "Expiring paste created with ID: $expiring_paste_id"
    print_json "$expiring_paste_response"
fi

# Retrieve the simple paste
print_section "5. Retrieving the Simple Paste"
echo "curl -s \"${API_URL}/${simple_paste_id}\""
retrieved_paste=$(curl -s "${API_URL}/${simple_paste_id}")

if [ $? -eq 0 ]; then
    print_success "Paste retrieved successfully"
    view_count=$(extract_json_value "$retrieved_paste" "viewCount")
    print_info "View count: $view_count"
    print_json "$retrieved_paste"
else
    print_error "Failed to retrieve paste"
fi

# Try to retrieve protected paste without password (should fail)
print_section "6. Attempting to Retrieve Protected Paste (without password)"
echo "curl -s \"${API_URL}/${protected_paste_id}\""
failed_response=$(curl -s "${API_URL}/${protected_paste_id}")
http_code=$(curl -s -o /dev/null -w "%{http_code}" "${API_URL}/${protected_paste_id}")

if [ "$http_code" = "403" ]; then
    print_success "Correctly denied access without password (HTTP $http_code)"
    print_json "$failed_response"
else
    print_error "Unexpected response code: $http_code"
fi

# Retrieve protected paste with password
print_section "7. Retrieving Protected Paste (with password)"
echo "curl -s \"${API_URL}/${protected_paste_id}?password=secret123\""
protected_retrieved=$(curl -s "${API_URL}/${protected_paste_id}?password=secret123")

if [ $? -eq 0 ]; then
    print_success "Protected paste retrieved successfully with password"
    print_json "$protected_retrieved"
else
    print_error "Failed to retrieve protected paste with password"
fi

# Update the simple paste
print_section "8. Updating the Simple Paste"
update_data='{
    "title": "Updated Hello World from curl",
    "content": "echo \"Hello, Updated World!\"\necho \"This paste has been updated via curl\"\necho \"Timestamp: $(date)\""
}'

echo "curl -X PUT \"${API_URL}/${simple_paste_id}\" \\"
echo "  -H \"Content-Type: application/json\" \\"
echo "  -d '${update_data}'"

updated_paste=$(curl -s -X PUT "${API_URL}/${simple_paste_id}" \
  -H "Content-Type: application/json" \
  -d "$update_data")

if [ $? -eq 0 ]; then
    print_success "Paste updated successfully"
    new_title=$(extract_json_value "$updated_paste" "title")
    print_info "New title: $new_title"
    print_json "$updated_paste"
else
    print_error "Failed to update paste"
fi

# Get public pastes
print_section "9. Getting Public Pastes (page 0, size 5)"
echo "curl -s \"${API_URL}/public?page=0&size=5\""
public_pastes=$(curl -s "${API_URL}/public?page=0&size=5")

if [ $? -eq 0 ]; then
    print_success "Public pastes retrieved"
    if command -v jq &> /dev/null; then
        total_elements=$(echo "$public_pastes" | jq -r '.totalElements')
        content_count=$(echo "$public_pastes" | jq -r '.content | length')
        print_info "Total elements: $total_elements, Current page: $content_count items"
    fi
    print_json "$public_pastes"
else
    print_error "Failed to retrieve public pastes"
fi

# Search for pastes
print_section "10. Searching for 'Hello' in Pastes"
echo "curl -s \"${API_URL}/search?q=Hello&size=3\""
search_results=$(curl -s "${API_URL}/search?q=Hello&size=3")

if [ $? -eq 0 ]; then
    print_success "Search completed"
    if command -v jq &> /dev/null; then
        result_count=$(echo "$search_results" | jq -r '.content | length')
        print_info "Found $result_count results"
    fi
    print_json "$search_results"
else
    print_error "Search failed"
fi

# Get pastes by language
print_section "11. Getting JavaScript Pastes"
echo "curl -s \"${API_URL}/language/javascript?size=3\""
js_pastes=$(curl -s "${API_URL}/language/javascript?size=3")

if [ $? -eq 0 ]; then
    print_success "JavaScript pastes retrieved"
    print_json "$js_pastes"
else
    print_error "Failed to retrieve JavaScript pastes"
fi

# Get recent pastes
print_section "12. Getting Recent Pastes (last 24 hours)"
echo "curl -s \"${API_URL}/recent?size=5\""
recent_pastes=$(curl -s "${API_URL}/recent?size=5")

if [ $? -eq 0 ]; then
    print_success "Recent pastes retrieved"
    print_json "$recent_pastes"
else
    print_error "Failed to retrieve recent pastes"
fi

# Get statistics
print_section "13. Getting Service Statistics"
echo "curl -s \"${API_URL}/stats\""
stats=$(curl -s "${API_URL}/stats")

if [ $? -eq 0 ]; then
    print_success "Statistics retrieved"
    if command -v jq &> /dev/null; then
        total_pastes=$(echo "$stats" | jq -r '.totalPastes')
        public_pastes=$(echo "$stats" | jq -r '.publicPastes')
        total_views=$(echo "$stats" | jq -r '.totalViews')
        print_info "Total pastes: $total_pastes, Public: $public_pastes, Total views: $total_views"
    fi
    print_json "$stats"
else
    print_error "Failed to retrieve statistics"
fi

# Raw content access
print_section "14. Getting Raw Content"
echo "curl -s \"${API_URL}/${simple_paste_id}\" -H \"Accept: application/json\""
raw_content=$(curl -s "${API_URL}/${simple_paste_id}" -H "Accept: application/json")
if [ $? -eq 0 ]; then
    print_success "Raw content retrieved"
    if command -v jq &> /dev/null; then
        content=$(echo "$raw_content" | jq -r '.content')
        echo "Content:"
        echo "$content"
    else
        print_json "$raw_content"
    fi
fi

# Delete test pastes
print_section "15. Cleanup - Deleting Test Pastes"

if [ -n "$simple_paste_id" ]; then
    echo "curl -X DELETE \"${API_URL}/${simple_paste_id}\""
    delete_response=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "${API_URL}/${simple_paste_id}")
    if [ "$delete_response" = "204" ]; then
        print_success "Simple paste deleted (HTTP $delete_response)"
    else
        print_error "Failed to delete simple paste (HTTP $delete_response)"
    fi
fi

if [ -n "$protected_paste_id" ]; then
    echo "curl -X DELETE \"${API_URL}/${protected_paste_id}\""
    delete_response=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "${API_URL}/${protected_paste_id}")
    if [ "$delete_response" = "204" ]; then
        print_success "Protected paste deleted (HTTP $delete_response)"
    else
        print_error "Failed to delete protected paste (HTTP $delete_response)"
    fi
fi

if [ -n "$expiring_paste_id" ]; then
    echo "curl -X DELETE \"${API_URL}/${expiring_paste_id}\""
    delete_response=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "${API_URL}/${expiring_paste_id}")
    if [ "$delete_response" = "204" ]; then
        print_success "Expiring paste deleted (HTTP $delete_response)"
    else
        print_error "Failed to delete expiring paste (HTTP $delete_response)"
    fi
fi

print_section "Example Complete!"
print_success "All curl examples have been executed successfully"

print_info "Tips:"
echo "  - Install 'jq' for better JSON formatting: brew install jq (macOS) or apt install jq (Ubuntu)"
echo "  - Set SECUREPASTE_URL environment variable to use a different server"
echo "  - Use -v flag with curl for verbose output and debugging"
echo "  - Check HTTP status codes with: curl -w \"%{http_code}\" ..."