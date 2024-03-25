package org.springframework.samples.petclinic.pettypes;

import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.models.EncryptionAlgorithm;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.samples.petclinic.owner.PetType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Perform some initializing of the supported pet types on startup by downloading them
 * from S3, if enabled
 */
@Component
public class InitPetTypes implements InitializingBean {

	private final Logger logger = LoggerFactory.getLogger(InitPetTypes.class);

	private final PetTypesRepository petTypesRepository;

	private final CryptographyClient cryptoClient;

	private final BlobClient blobClient;

	@Value("${app.init.pet-types.blob:petclinic-pettypes.txt}")
	private String petTypesInitBlobName;

	@Value("${app.init.pet-types.keyvault-encrypted:false}")
	private Boolean petTypesInitKeyVaultEncrypted;

	InitPetTypes(PetTypesRepository petTypesRepository, BlobServiceClient blobServiceClient) {
		this.petTypesRepository = petTypesRepository;
		this.blobClient = blobServiceClient
			.getBlobContainerClient("spring-petclinic-init")
			.getBlobClient(petTypesInitBlobName);

		this.cryptoClient = new CryptographyClientBuilder().keyIdentifier("spring-petclinic-init").buildClient();
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (blobClient == null) {
			logger.info("No BlobClient configured, skipping loading pettypes.");
			return;
		}

		logger.info("Loading Pet types from \"spring-petclinic-init\" container at " + petTypesInitBlobName);
		byte[] fileContents = blobClient.downloadContent().toBytes();

		// if keyvault encrypted, attempt to decrypt it
		if (petTypesInitKeyVaultEncrypted) {
			logger.info("Decrypting pet types using KeyVault key...");
			fileContents = cryptoClient.decrypt(EncryptionAlgorithm.RSA_OAEP, fileContents).getPlainText();
		}

		List<PetType> foundTypes = new String(fileContents, StandardCharsets.UTF_8).lines().map(s -> {
			PetType type = new PetType();
			type.setName(s);
			return type;
		}).toList();
		logger.info("Found " + foundTypes.size() + " pet types");

		// load the found types into the database
		if (!foundTypes.isEmpty()) {
			petTypesRepository.saveAllAndFlush(foundTypes);

			// clean up the file if we've successfully loaded from it
			logger.info("Deleting Pet types blob from container");
			blobClient.delete();
		}
	}

}
