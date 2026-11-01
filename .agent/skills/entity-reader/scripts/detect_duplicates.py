"""
Detect duplicate or highly similar table structures in parsed entity metadata.
Used by entity-reader skill to prevent redundant documentation.
"""

from typing import List, Dict, Tuple, Set
from dataclasses import dataclass


@dataclass
class TableSimilarity:
    """Represents similarity between two tables."""
    table1: str
    table2: str
    similarity_percent: float
    shared_columns: List[str]
    table1_only: List[str]
    table2_only: List[str]
    column_count1: int
    column_count2: int


def extract_column_names(entity: Dict) -> Set[str]:
    """Extract column names from an entity's field list."""
    columns = set()
    for field in entity.get('fields', []):
        col_name = field.get('column_name', '').lower().strip()
        if col_name:
            columns.add(col_name)
    return columns


def calculate_similarity(cols1: Set[str], cols2: Set[str]) -> float:
    """
    Calculate similarity percentage between two column sets.
    Uses the smaller table as the denominator to catch subset relationships.
    """
    if not cols1 or not cols2:
        return 0.0
    
    shared = cols1.intersection(cols2)
    smaller_table_size = min(len(cols1), len(cols2))
    
    if smaller_table_size == 0:
        return 0.0
    
    return (len(shared) / smaller_table_size) * 100


def detect_duplicate_tables(entities: List[Dict], threshold: float = 90.0) -> List[TableSimilarity]:
    """
    Detect duplicate or highly similar table structures.
    
    Args:
        entities: List of entity dictionaries with 'name', 'table_name', and 'fields'
        threshold: Similarity percentage threshold (default 90%)
    
    Returns:
        List of TableSimilarity objects for pairs exceeding the threshold
    """
    duplicates = []
    
    # Compare each pair of tables
    for i in range(len(entities)):
        for j in range(i + 1, len(entities)):
            entity1 = entities[i]
            entity2 = entities[j]
            
            table1_name = entity1.get('table_name', entity1.get('name', 'unknown'))
            table2_name = entity2.get('table_name', entity2.get('name', 'unknown'))
            
            cols1 = extract_column_names(entity1)
            cols2 = extract_column_names(entity2)
            
            if not cols1 or not cols2:
                continue
            
            similarity = calculate_similarity(cols1, cols2)
            
            if similarity >= threshold:
                shared = sorted(cols1.intersection(cols2))
                only1 = sorted(cols1 - cols2)
                only2 = sorted(cols2 - cols1)
                
                duplicates.append(TableSimilarity(
                    table1=table1_name,
                    table2=table2_name,
                    similarity_percent=similarity,
                    shared_columns=shared,
                    table1_only=only1,
                    table2_only=only2,
                    column_count1=len(cols1),
                    column_count2=len(cols2)
                ))
    
    return duplicates


def format_duplicate_report(similarity: TableSimilarity) -> str:
    """Format a human-readable duplicate detection report."""
    report = []
    report.append(f"\n{'='*70}")
    report.append(f"POTENTIAL DUPLICATE DETECTED")
    report.append(f"{'='*70}")
    report.append(f"Table 1: {similarity.table1} ({similarity.column_count1} columns)")
    report.append(f"Table 2: {similarity.table2} ({similarity.column_count2} columns)")
    report.append(f"Similarity: {similarity.similarity_percent:.1f}%")
    report.append(f"\nShared columns ({len(similarity.shared_columns)}):")
    
    for col in similarity.shared_columns[:10]:  # Show first 10
        report.append(f"  - {col}")
    
    if len(similarity.shared_columns) > 10:
        report.append(f"  ... and {len(similarity.shared_columns) - 10} more")
    
    if similarity.table1_only:
        report.append(f"\nOnly in {similarity.table1}:")
        for col in similarity.table1_only[:5]:
            report.append(f"  - {col}")
        if len(similarity.table1_only) > 5:
            report.append(f"  ... and {len(similarity.table1_only) - 5} more")
    
    if similarity.table2_only:
        report.append(f"\nOnly in {similarity.table2}:")
        for col in similarity.table2_only[:5]:
            report.append(f"  - {col}")
        if len(similarity.table2_only) > 5:
            report.append(f"  ... and {len(similarity.table2_only) - 5} more")
    
    report.append(f"{'='*70}\n")
    
    return "\n".join(report)


def filter_entities_by_user_choice(
    entities: List[Dict],
    duplicates: List[TableSimilarity],
    user_choices: Dict[str, str]
) -> List[Dict]:
    """
    Filter entity list based on user's duplicate handling choices.
    
    Args:
        entities: Original entity list
        duplicates: Detected duplicates
        user_choices: Dict mapping duplicate pair IDs to choices ('keep_first', 'keep_second', 'keep_both', 'keep_both_annotated')
    
    Returns:
        Filtered entity list
    """
    tables_to_skip = set()
    tables_to_annotate = {}  # table_name -> other_table_name
    
    for i, dup in enumerate(duplicates):
        choice_key = f"dup_{i}"
        choice = user_choices.get(choice_key, 'keep_both')
        
        if choice == 'keep_first':
            tables_to_skip.add(dup.table2)
        elif choice == 'keep_second':
            tables_to_skip.add(dup.table1)
        elif choice == 'keep_both_annotated':
            tables_to_annotate[dup.table1] = dup.table2
            tables_to_annotate[dup.table2] = dup.table1
    
    # Filter entities
    filtered = []
    for entity in entities:
        table_name = entity.get('table_name', entity.get('name', ''))
        
        if table_name in tables_to_skip:
            continue
        
        # Add annotation if needed
        if table_name in tables_to_annotate:
            other_table = tables_to_annotate[table_name]
            annotation = f"[Note: Similar structure to {other_table}]"
            
            # Add annotation to first field's description or create a note field
            if entity.get('fields'):
                first_field = entity['fields'][0]
                current_desc = first_field.get('description', '')
                if current_desc:
                    first_field['description'] = f"{annotation} {current_desc}"
                else:
                    first_field['description'] = annotation
            
            # Also store at entity level for summary purposes
            entity['duplicate_note'] = annotation
        
        filtered.append(entity)
    
    return filtered


if __name__ == '__main__':
    # Example usage
    test_entities = [
        {
            'name': 'User',
            'table_name': 'users',
            'fields': [
                {'column_name': 'id'},
                {'column_name': 'email'},
                {'column_name': 'created_at'}
            ]
        },
        {
            'name': 'UserBackup',
            'table_name': 'users_backup',
            'fields': [
                {'column_name': 'id'},
                {'column_name': 'email'},
                {'column_name': 'created_at'},
                {'column_name': 'backup_date'}
            ]
        },
        {
            'name': 'Product',
            'table_name': 'products',
            'fields': [
                {'column_name': 'id'},
                {'column_name': 'name'},
                {'column_name': 'price'}
            ]
        }
    ]
    
    duplicates = detect_duplicate_tables(test_entities, threshold=90.0)
    
    for dup in duplicates:
        print(format_duplicate_report(dup))
