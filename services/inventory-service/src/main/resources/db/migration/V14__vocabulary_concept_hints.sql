ALTER TABLE vocabularies ADD COLUMN IF NOT EXISTS concept_hints TEXT;

UPDATE vocabularies SET concept_hints =
  'PersonalData, SpecialCategoryPersonalData, SensitivePersonalData, Identifier'
WHERE prefix = 'dpv';

UPDATE vocabularies SET concept_hints =
  'EmailAddress (https://w3id.org/dpv/dpv-pd#EmailAddress), '
  'Name (https://w3id.org/dpv/dpv-pd#Name), '
  'PhoneNumber (https://w3id.org/dpv/dpv-pd#PhoneNumber), '
  'PhysicalAddress (https://w3id.org/dpv/dpv-pd#PhysicalAddress), '
  'IPAddress (https://w3id.org/dpv/dpv-pd#IPAddress), '
  'NationalIdentificationNumber (https://w3id.org/dpv/dpv-pd#NationalIdentificationNumber), '
  'PassportNumber (https://w3id.org/dpv/dpv-pd#PassportNumber), '
  'TaxID (https://w3id.org/dpv/dpv-pd#TaxID), '
  'SocialSecurityNumber (https://w3id.org/dpv/dpv-pd#SocialSecurityNumber), '
  'BirthDate (https://w3id.org/dpv/dpv-pd#BirthDate), '
  'Age (https://w3id.org/dpv/dpv-pd#Age), '
  'Gender (https://w3id.org/dpv/dpv-pd#Gender), '
  'Nationality (https://w3id.org/dpv/dpv-pd#Nationality), '
  'Income (https://w3id.org/dpv/dpv-pd#Income), '
  'BankAccountNumber (https://w3id.org/dpv/dpv-pd#BankAccountNumber), '
  'HealthData (https://w3id.org/dpv/dpv-pd#HealthData), '
  'MedicalHealth (https://w3id.org/dpv/dpv-pd#MedicalHealth), '
  'Biometric (https://w3id.org/dpv/dpv-pd#Biometric), '
  'Location (https://w3id.org/dpv/dpv-pd#Location), '
  'UserAgent (https://w3id.org/dpv/dpv-pd#UserAgent), '
  'Username (https://w3id.org/dpv/dpv-pd#Username), '
  'Password (https://w3id.org/dpv/dpv-pd#Password)'
WHERE prefix = 'dpv-pd';
