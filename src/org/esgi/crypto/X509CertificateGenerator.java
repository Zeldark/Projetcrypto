package org.esgi.crypto;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Calendar;
import java.util.Date;

import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERBMPString;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.DigestInfo;
import org.bouncycastle.asn1.x509.RSAPublicKeyStructure;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.TBSCertificateStructure;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator;
import org.bouncycastle.asn1.x509.X509CertificateStructure;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.crypto.AsymmetricBlockCipher;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.encodings.PKCS1Encoding;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.jce.PrincipalUtil;
import org.bouncycastle.jce.interfaces.PKCS12BagAttributeCarrier;
import org.bouncycastle.jce.provider.X509CertificateObject;
import org.esgi.crypto.*;
import org.bouncycastle.x509.extension.SubjectKeyIdentifierStructure;


public class X509CertificateGenerator {

	private X509Certificate caCert;
	private RSAPrivateCrtKeyParameters caPrivateKey;
	private KeyStore caKs;
	
	
	public KeyStore getCaKs() {
		return caKs;
	}

	public void setCaKs(KeyStore caKs) {
		this.caKs = caKs;
	}

	public RSAPrivateCrtKeyParameters getCaPrivateKey() {
		return caPrivateKey;
	}

	public void setCaPrivateKey(RSAPrivateCrtKeyParameters caPrivateKey) {
		this.caPrivateKey = caPrivateKey;
	}

	private boolean useBCAPI;
	
	public X509CertificateGenerator(String caFile, String caPassword, String caAlias, boolean useBCAPI) 
			throws KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException, UnrecoverableKeyException, InvalidKeyException, NoSuchProviderException, SignatureException {
		this.useBCAPI = useBCAPI;
		
		System.out.println("Chargement du certificat et de la clé privée du fichier '" + caFile + "', en utilisant l'alias '" + caAlias + "' avec "
				+ (this.useBCAPI ? "Bouncycastle lightweight API" : "JCE API"));
		KeyStore caKs = KeyStore.getInstance("PKCS12");
		caKs.load(new FileInputStream(new File(caFile)), caPassword.toCharArray());

		Key key = caKs.getKey(caAlias, caPassword.toCharArray());
		if (key == null) {
			throw new RuntimeException("Keystore vide !"); 
		}
		RSAPrivateCrtKey privKey = (RSAPrivateCrtKey) key;
		caPrivateKey = new RSAPrivateCrtKeyParameters(privKey.getModulus(), privKey.getPublicExponent(), privKey.getPrivateExponent(),
				privKey.getPrimeP(), privKey.getPrimeQ(), privKey.getPrimeExponentP(), privKey.getPrimeExponentQ(), privKey.getCrtCoefficient());

		caCert = (X509Certificate) caKs.getCertificate(caAlias);
		if (caCert == null) {
			throw new RuntimeException("Certificat vide !"); 
		}
		System.out.println("clé privée et certificat chargé DN= '" + caCert.getSubjectDN().getName() + "'");
		caCert.verify(caCert.getPublicKey());
		System.out.println("Certificat vérifié avec sa clé publique");
	}
	
	public boolean createCertificate(String dn, int validityDays, String exportFile, String exportPassword) throws 
			IOException, InvalidKeyException, SecurityException, SignatureException, NoSuchAlgorithmException, DataLengthException, CryptoException, KeyStoreException, NoSuchProviderException, CertificateException, InvalidKeySpecException {
		System.out.println("Génération de certificat pour DN = '" + 
				dn + "', valide pour " + validityDays + " jours");
		SecureRandom sr = new SecureRandom();
		
		PublicKey pubKey;
		PrivateKey privKey;
		
		System.out.println("Creation de la paire de clé RSA");
		// genere la paire de clé
		if (useBCAPI) {
			RSAKeyPairGenerator gen = new RSAKeyPairGenerator();
			gen.init(new RSAKeyGenerationParameters(BigInteger.valueOf(3), sr, 1024, 80));
			AsymmetricCipherKeyPair keypair = gen.generateKeyPair();
			System.out.println("Generated keypair, extracting components and creating public structure for certificate");
			RSAKeyParameters publicKey = (RSAKeyParameters) keypair.getPublic();
			RSAPrivateCrtKeyParameters privateKey = (RSAPrivateCrtKeyParameters) keypair.getPrivate();
			// used to get proper encoding for the certificate
			RSAPublicKeyStructure pkStruct = new RSAPublicKeyStructure(publicKey.getModulus(), publicKey.getExponent());
			System.out.println("New public key is '" + new String(Hex.encodeHex(pkStruct.getEncoded())) + 
					", exponent=" + publicKey.getExponent() + ", modulus=" + publicKey.getModulus());
			// JCE format needed for the certificate - because getEncoded() is necessary...
	        pubKey = KeyFactory.getInstance("RSA").generatePublic(
	        		new RSAPublicKeySpec(publicKey.getModulus(), publicKey.getExponent()));
	        // and this one for the KeyStore
	        privKey = KeyFactory.getInstance("RSA").generatePrivate(
	        		new RSAPrivateCrtKeySpec(publicKey.getModulus(), publicKey.getExponent(),
	        				privateKey.getExponent(), privateKey.getP(), privateKey.getQ(), 
	        				privateKey.getDP(), privateKey.getDQ(), privateKey.getQInv()));
		}
		else {
			// this is the JSSE way of key generation
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
			keyGen.initialize(1024, sr);
			KeyPair keypair = keyGen.generateKeyPair();
			privKey = keypair.getPrivate();
			pubKey = keypair.getPublic();
		}
	    
		Calendar expiry = Calendar.getInstance();
		expiry.add(Calendar.DAY_OF_YEAR, validityDays);
 
		X509Name x509Name = new X509Name("CN=" + dn);

		V3TBSCertificateGenerator certGen = new V3TBSCertificateGenerator();
	    certGen.setSerialNumber(new DERInteger(BigInteger.valueOf(System.currentTimeMillis())));
		certGen.setIssuer(PrincipalUtil.getSubjectX509Principal(caCert));
		certGen.setSubject(x509Name);
		DERObjectIdentifier sigOID = X509Util.getAlgorithmOID("SHA1WithRSAEncryption");
		AlgorithmIdentifier sigAlgId = new AlgorithmIdentifier(sigOID, new DERNull());
		certGen.setSignature(sigAlgId);
		certGen.setSubjectPublicKeyInfo(new SubjectPublicKeyInfo((ASN1Sequence)new ASN1InputStream(
                new ByteArrayInputStream(pubKey.getEncoded())).readObject()));
		certGen.setStartDate(new Time(new Date(System.currentTimeMillis())));
		certGen.setEndDate(new Time(expiry.getTime()));
		
		System.out.println("Certificate structure generated, creating SHA1 digest");
		// attention: hard coded to be SHA1+RSA!
		SHA1Digest digester = new SHA1Digest();
		AsymmetricBlockCipher rsa = new PKCS1Encoding(new RSAEngine());
		TBSCertificateStructure tbsCert = certGen.generateTBSCertificate();

		ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
		DEROutputStream         dOut = new DEROutputStream(bOut);
		dOut.writeObject(tbsCert);

		// and now sign
		byte[] signature;
		if (useBCAPI) {
			byte[] certBlock = bOut.toByteArray();
			// first create digest
			System.out.println("Block to sign is '" + new String(Hex.encodeHex(certBlock)) + "'");		
			digester.update(certBlock, 0, certBlock.length);
			byte[] hash = new byte[digester.getDigestSize()];
			digester.doFinal(hash, 0);
			// and sign that
			rsa.init(true, caPrivateKey);
			DigestInfo dInfo = new DigestInfo( new AlgorithmIdentifier(X509ObjectIdentifiers.id_SHA1, null), hash);
			byte[] digest = dInfo.getEncoded(ASN1Encodable.DER);
			signature = rsa.processBlock(digest, 0, digest.length);
		}
		else {
			// or the JCE way
	        PrivateKey caPrivKey = KeyFactory.getInstance("RSA").generatePrivate(
	        		new RSAPrivateCrtKeySpec(caPrivateKey.getModulus(), caPrivateKey.getPublicExponent(),
	        				caPrivateKey.getExponent(), caPrivateKey.getP(), caPrivateKey.getQ(), 
	        				caPrivateKey.getDP(), caPrivateKey.getDQ(), caPrivateKey.getQInv()));
			
	        Signature sig = Signature.getInstance(sigOID.getId());
	        sig.initSign(caPrivKey, sr);
	        sig.update(bOut.toByteArray());
	        signature = sig.sign();
		}
		System.out.println("SHA1/RSA signature of digest is '" + new String(Hex.encodeHex(signature)) + "'");

		// and finally construct the certificate structure
        ASN1EncodableVector  v = new ASN1EncodableVector();

        v.add(tbsCert);
        v.add(sigAlgId);
        v.add(new DERBitString(signature));

        X509CertificateObject clientCert = new X509CertificateObject(new X509CertificateStructure(new DERSequence(v))); 
        System.out.println("Verifying certificate for correct signature with CA public key");
        clientCert.verify(caCert.getPublicKey());

        // and export as PKCS12 formatted file along with the private key and the CA certificate 
        System.out.println("Exporting certificate in PKCS12 format");

        PKCS12BagAttributeCarrier bagCert = clientCert;
        bagCert.setBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_friendlyName,
        		new DERBMPString("Certificate for IPSec WLAN access"));
        bagCert.setBagAttribute(
                PKCSObjectIdentifiers.pkcs_9_at_localKeyId,
                new SubjectKeyIdentifierStructure(pubKey));
        
        KeyStore store = KeyStore.getInstance("PKCS12");

        store.load(null, null);

        X509Certificate[] chain = new X509Certificate[2];
        // first the client, then the CA certificate
        chain[0] = clientCert;
        chain[1] = caCert;
        
        store.setKeyEntry("Private key for IPSec WLAN access", privKey, exportPassword.toCharArray(), chain);

        FileOutputStream fOut = new FileOutputStream(exportFile);

        store.store(fOut, exportPassword.toCharArray());
		
        return true;
	}
	
	/** The test CA can e.g. be created with
	 * 
	 * echo -e "AT\nUpper Austria\nSteyr\nMy Organization\nNetwork tests\nTest CA certificate\nme@myserver.com\n\n\n" | \
	     openssl req -new -x509 -outform PEM -newkey rsa:2048 -nodes -keyout /tmp/ca.key -keyform PEM -out /tmp/ca.crt -days 365;
	   echo "test password" | openssl pkcs12 -export -in /tmp/ca.crt -inkey /tmp/ca.key -out ca.p12 -name "Test CA" -passout stdin
	 * 
	 * The created certificate can be displayed with
	 * 
	 * openssl pkcs12 -nodes -info -in test.p12 > /tmp/test.cert && openssl x509 -noout -text -in /tmp/test.cert
	 */
	
}
