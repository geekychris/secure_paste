#!/usr/bin/env python3
"""
SecurePaste Python API Client Example

This script demonstrates how to interact with the SecurePaste API
using Python and the requests library.
"""

import requests
import json
import time
import sys
from datetime import datetime


class SecurePasteClient:
    """Python client for SecurePaste API"""
    
    def __init__(self, base_url="http://localhost:8080"):
        self.base_url = base_url.rstrip("/")
        self.api_base = f"{self.base_url}/api/pastes"
        self.session = requests.Session()
        self.session.headers.update({
            'Content-Type': 'application/json',
            'User-Agent': 'SecurePaste-Python-Client/1.0'
        })
    
    def create_paste(self, title, content, **kwargs):
        """
        Create a new paste
        
        Args:
            title (str): Paste title
            content (str): Paste content
            **kwargs: Optional parameters (language, authorName, authorEmail, 
                     visibility, expirationMinutes, password)
        
        Returns:
            dict: Created paste information
        """
        data = {
            'title': title,
            'content': content,
            **kwargs
        }
        
        try:
            response = self.session.post(self.api_base, json=data)
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            print(f"Error creating paste: {e}")
            if hasattr(e, 'response') and e.response:
                print(f"Response: {e.response.text}")
            return None
    
    def get_paste(self, paste_id, password=None):
        """
        Retrieve a paste by ID
        
        Args:
            paste_id (str): Paste ID
            password (str, optional): Password for protected pastes
        
        Returns:
            dict: Paste information
        """
        params = {}
        if password:
            params['password'] = password
        
        try:
            response = self.session.get(f"{self.api_base}/{paste_id}", params=params)
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            print(f"Error retrieving paste {paste_id}: {e}")
            if hasattr(e, 'response') and e.response:
                print(f"Response: {e.response.text}")
            return None
    
    def update_paste(self, paste_id, **kwargs):
        """
        Update an existing paste
        
        Args:
            paste_id (str): Paste ID
            **kwargs: Fields to update (title, content, language, visibility)
        
        Returns:
            dict: Updated paste information
        """
        try:
            response = self.session.put(f"{self.api_base}/{paste_id}", json=kwargs)
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            print(f"Error updating paste {paste_id}: {e}")
            if hasattr(e, 'response') and e.response:
                print(f"Response: {e.response.text}")
            return None
    
    def delete_paste(self, paste_id):
        """
        Delete a paste
        
        Args:
            paste_id (str): Paste ID
        
        Returns:
            bool: True if successful
        """
        try:
            response = self.session.delete(f"{self.api_base}/{paste_id}")
            response.raise_for_status()
            return True
        except requests.RequestException as e:
            print(f"Error deleting paste {paste_id}: {e}")
            return False
    
    def get_public_pastes(self, page=0, size=10):
        """
        Get public pastes
        
        Args:
            page (int): Page number (0-based)
            size (int): Page size
        
        Returns:
            dict: Paginated paste results
        """
        params = {'page': page, 'size': size}
        try:
            response = self.session.get(f"{self.api_base}/public", params=params)
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            print(f"Error getting public pastes: {e}")
            return None
    
    def search_pastes(self, query, page=0, size=10):
        """
        Search pastes by content or title
        
        Args:
            query (str): Search term
            page (int): Page number (0-based)
            size (int): Page size
        
        Returns:
            dict: Paginated search results
        """
        params = {'q': query, 'page': page, 'size': size}
        try:
            response = self.session.get(f"{self.api_base}/search", params=params)
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            print(f"Error searching pastes: {e}")
            return None
    
    def get_pastes_by_language(self, language, page=0, size=10):
        """
        Get pastes by programming language
        
        Args:
            language (str): Programming language
            page (int): Page number (0-based)
            size (int): Page size
        
        Returns:
            dict: Paginated paste results
        """
        params = {'page': page, 'size': size}
        try:
            response = self.session.get(f"{self.api_base}/language/{language}", params=params)
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            print(f"Error getting pastes for language {language}: {e}")
            return None
    
    def get_statistics(self):
        """
        Get paste statistics
        
        Returns:
            dict: Statistics information
        """
        try:
            response = self.session.get(f"{self.api_base}/stats")
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            print(f"Error getting statistics: {e}")
            return None
    
    def health_check(self):
        """
        Check if the service is healthy
        
        Returns:
            bool: True if service is healthy
        """
        try:
            response = self.session.get(f"{self.api_base}/health")
            response.raise_for_status()
            return response.json().get('status') == 'UP'
        except requests.RequestException:
            return False


def print_paste_info(paste):
    """Print paste information in a formatted way"""
    if not paste:
        return
    
    print(f"ID: {paste['id']}")
    print(f"Title: {paste['title']}")
    print(f"Language: {paste.get('language', 'N/A')}")
    print(f"Author: {paste.get('authorName', 'Anonymous')}")
    print(f"Visibility: {paste['visibility']}")
    print(f"Views: {paste['viewCount']}")
    print(f"Created: {paste['createdAt']}")
    if paste.get('expiresAt'):
        print(f"Expires: {paste['expiresAt']}")
    if paste.get('passwordProtected'):
        print("Password Protected: Yes")
    print(f"Content Preview: {paste['content'][:100]}...")
    print("-" * 50)


def main():
    """Main example function"""
    print("SecurePaste Python API Client Example")
    print("=" * 50)
    
    # Initialize client
    client = SecurePasteClient()
    
    # Health check
    print("1. Health Check")
    if client.health_check():
        print("✓ Service is healthy")
    else:
        print("✗ Service is not available")
        sys.exit(1)
    
    # Create a simple paste
    print("\n2. Creating a simple paste")
    simple_paste = client.create_paste(
        title="Hello World Example",
        content='print("Hello, World from Python!")',
        language="python",
        authorName="API Example",
        visibility="PUBLIC"
    )
    
    if simple_paste:
        print("✓ Paste created successfully")
        print_paste_info(simple_paste)
        simple_paste_id = simple_paste['id']
    else:
        print("✗ Failed to create paste")
        return
    
    # Create a password-protected paste
    print("\n3. Creating a password-protected paste")
    protected_paste = client.create_paste(
        title="Secret Code",
        content="const secret = 'This is a secret message!';",
        language="javascript",
        visibility="UNLISTED",
        password="secret123"
    )
    
    if protected_paste:
        print("✓ Protected paste created successfully")
        print_paste_info(protected_paste)
        protected_paste_id = protected_paste['id']
    else:
        print("✗ Failed to create protected paste")
        return
    
    # Create a paste with expiration
    print("\n4. Creating a paste that expires in 60 minutes")
    expiring_paste = client.create_paste(
        title="Temporary Code Snippet",
        content="// This paste will expire in 60 minutes\nconsole.log('Temporary message');",
        language="javascript",
        expirationMinutes=60,
        visibility="PUBLIC"
    )
    
    if expiring_paste:
        print("✓ Expiring paste created successfully")
        print_paste_info(expiring_paste)
    
    # Retrieve pastes
    print("\n5. Retrieving the simple paste")
    retrieved = client.get_paste(simple_paste_id)
    if retrieved:
        print("✓ Paste retrieved successfully")
        print(f"View count increased to: {retrieved['viewCount']}")
    
    # Try to retrieve protected paste without password
    print("\n6. Attempting to retrieve protected paste without password")
    failed_retrieval = client.get_paste(protected_paste_id)
    if not failed_retrieval:
        print("✓ Correctly denied access without password")
    
    # Retrieve protected paste with password
    print("\n7. Retrieving protected paste with password")
    protected_retrieved = client.get_paste(protected_paste_id, password="secret123")
    if protected_retrieved:
        print("✓ Protected paste retrieved successfully with password")
    
    # Update a paste
    print("\n8. Updating the simple paste")
    updated = client.update_paste(
        simple_paste_id,
        title="Updated Hello World Example",
        content='print("Hello, Updated World from Python!")'
    )
    if updated:
        print("✓ Paste updated successfully")
        print(f"New title: {updated['title']}")
    
    # Search for pastes
    print("\n9. Searching for Python pastes")
    search_results = client.search_pastes("Python", size=5)
    if search_results and search_results['content']:
        print(f"✓ Found {len(search_results['content'])} Python-related pastes")
        for paste in search_results['content'][:2]:  # Show first 2 results
            print_paste_info(paste)
    
    # Get pastes by language
    print("\n10. Getting JavaScript pastes")
    js_pastes = client.get_pastes_by_language("javascript", size=3)
    if js_pastes and js_pastes['content']:
        print(f"✓ Found {len(js_pastes['content'])} JavaScript pastes")
    
    # Get public pastes
    print("\n11. Getting recent public pastes")
    public_pastes = client.get_public_pastes(size=5)
    if public_pastes and public_pastes['content']:
        print(f"✓ Retrieved {len(public_pastes['content'])} public pastes")
        print(f"Total public pastes: {public_pastes['totalElements']}")
    
    # Get statistics
    print("\n12. Getting service statistics")
    stats = client.get_statistics()
    if stats:
        print("✓ Statistics retrieved successfully")
        print(f"Total pastes: {stats.get('totalPastes', 0)}")
        print(f"Public pastes: {stats.get('publicPastes', 0)}")
        print(f"Total views: {stats.get('totalViews', 0)}")
        popular_langs = stats.get('popularLanguages', [])
        if popular_langs:
            print("Popular languages:")
            for lang_data in popular_langs[:5]:  # Show top 5
                print(f"  - {lang_data[0]}: {lang_data[1]} pastes")
    
    # Clean up - delete the test pastes
    print("\n13. Cleaning up test pastes")
    if client.delete_paste(simple_paste_id):
        print("✓ Simple paste deleted")
    if client.delete_paste(protected_paste_id):
        print("✓ Protected paste deleted")
    
    print("\n✓ Example completed successfully!")


if __name__ == "__main__":
    main()